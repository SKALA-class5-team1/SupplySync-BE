package com.skala.supplyquote.supplyquote_backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.skala.supplyquote.supplyquote_backend.messaging.AnalysisDispatch;
import com.skala.supplyquote.supplyquote_backend.messaging.contract.MessagingContract;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 실제 RabbitMQ 브로커(Testcontainers)와 가짜 Agent HTTP 서버로 outbox 발행 → 큐 → Spring 소비자 → Agent 호출을
 * 끝까지 확인한다. Docker가 필요하다. 일반 테스트와 달리 트랜잭션 롤백에 기대지 않으므로 직접 정리한다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles({"test", "demo"})
@TestPropertySource(properties = {
        "app.messaging.enabled=true",
        "app.messaging.relay-delay-ms=200",
        "app.watchdog.enabled=false"})
class AnalysisMessagingIntegrationTests {

    private record Call(String path, String apiKey, String body) { }

    private static final GenericContainer<?> RABBIT = new GenericContainer<>(
            DockerImageName.parse("rabbitmq:3.13-management"))
            .withExposedPorts(5672)
            .withEnv("RABBITMQ_DEFAULT_USER", "supplysync")
            .withEnv("RABBITMQ_DEFAULT_PASS", "test")
            .waitingFor(Wait.forLogMessage(".*Server startup complete.*", 1));
    private static final CopyOnWriteArrayList<Call> CALLS = new CopyOnWriteArrayList<>();
    private static final AtomicInteger AGENT_STATUS = new AtomicInteger(202);
    private static final HttpServer AGENT;

    static {
        RABBIT.start();
        try {
            AGENT = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        AGENT.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            CALLS.add(new Call(exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("X-Internal-Api-Key"), body));
            exchange.sendResponseHeaders(AGENT_STATUS.get(), -1);
            exchange.close();
        });
        AGENT.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", () -> RABBIT.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", () -> "supplysync");
        registry.add("app.agent.base-url", () -> "http://127.0.0.1:" + AGENT.getAddress().getPort());
    }

    @AfterAll
    static void stopAgent() {
        AGENT.stop(0);
    }

    @Autowired AnalysisDispatch dispatch;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;
    @Autowired RabbitTemplate rabbit;
    @Autowired RabbitAdmin admin;

    @BeforeEach
    void reset() {
        CALLS.clear();
        AGENT_STATUS.set(202);
        admin.purgeQueue(MessagingContract.QUEUE_ANALYSIS_REQUEST, true);
        admin.purgeQueue(MessagingContract.QUEUE_ANALYSIS_REQUEST_DLQ, true);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from analysis_outbox");
        jdbc.update("delete from processed_messages");
        jdbc.update("delete from ai_tasks where thread_id like 'task-it-%'");
    }

    @Test
    void startMessageTravelsThroughTheBrokerAndCallsTheAgentOnce() {
        Task task = newTask("QUEUED");
        dispatch.requested(task.id, 1L, 2L, "HB-EV7-EWP-001", task.thread, task.requestId);

        waitUntil(() -> "RUNNING".equals(statusOf(task)), "작업이 RUNNING이 되지 않았습니다");

        assertThat(CALLS).hasSize(1);
        Call call = CALLS.getFirst();
        assertThat(call.path).isEqualTo("/runs");
        assertThat(call.apiKey).isEqualTo("test-internal-key");
        JsonNode body = json.readTree(call.body);
        assertThat(body.get("task_id").asString()).isEqualTo(task.id.toString());
        assertThat(body.get("rfq_number").asString()).isEqualTo("HB-EV7-EWP-001");
        assertThat(body.get("thread_id").asString()).isEqualTo(task.thread);
        assertThat(body.get("request_id").asString()).isEqualTo(task.requestId.toString());
        assertThat(jdbc.queryForObject("select count(*) from analysis_outbox where published_at is not null",
                Integer.class)).isEqualTo(1);
        assertThat(rabbit.receive(MessagingContract.QUEUE_ANALYSIS_REQUEST_DLQ, 500)).isNull();
    }

    @Test
    void agentFailureFailsTheTaskWithoutRetryAndDeadLettersTheMessage() {
        AGENT_STATUS.set(500);
        Task task = newTask("QUEUED");
        dispatch.requested(task.id, 1L, 2L, "HB-EV7-EWP-001", task.thread, task.requestId);

        waitUntil(() -> "FAILED".equals(statusOf(task)), "작업이 FAILED가 되지 않았습니다");

        assertThat(CALLS).hasSize(1);
        assertThat(jdbc.queryForObject("select failure_reason from ai_tasks where task_id=?", String.class, task.id))
                .contains("500");
        assertThat(rabbit.receive(MessagingContract.QUEUE_ANALYSIS_REQUEST_DLQ, 10_000)).isNotNull();
    }

    @Test
    void resumeMessagesReachTheAgentInTheAgreedFormats() {
        Task task = newTask("RUNNING");
        dispatch.clarification(task.id, task.thread, 2L, List.of(Map.of("field", "spec.flow", "value", "22 L/min")));
        dispatch.approval(task.id, task.thread, 2L,
                Map.of("result", "REVISE", "reason", "조건 수정", "changes", Map.of()));

        waitUntil(() -> CALLS.size() == 2, "재개 호출 2건이 도착하지 않았습니다");

        String resumePath = "/runs/" + task.thread + "/resume";
        assertThat(CALLS).allSatisfy(call -> {
            assertThat(call.path).isEqualTo(resumePath);
            assertThat(call.apiKey).isEqualTo("test-internal-key");
        });
        JsonNode clarification = CALLS.stream().map(c -> json.readTree(c.body))
                .filter(n -> "CLARIFICATION".equals(n.path("kind").asString())).findFirst().orElseThrow();
        assertThat(clarification.get("userId").asLong()).isEqualTo(2L);
        assertThat(clarification.get("decision").get("assumptions").get(0).get("field").asString())
                .isEqualTo("spec.flow");
        JsonNode approval = CALLS.stream().map(c -> json.readTree(c.body))
                .filter(n -> "SG4.request_approval".equals(n.path("step").asString())).findFirst().orElseThrow();
        assertThat(approval.get("userId").asLong()).isEqualTo(2L);
        assertThat(approval.get("decision").get("result").asString()).isEqualTo("REVISE");
        assertThat(approval.has("quoteId")).isFalse();
    }

    private record Task(UUID id, String thread, UUID requestId) { }

    private Task newTask(String status) {
        UUID id = UUID.randomUUID();
        Task task = new Task(id, "task-it-" + id, UUID.randomUUID());
        jdbc.update("insert into ai_tasks(task_id,rfq_number,requested_by,status,thread_id,request_id) "
                + "values (?,'HB-EV7-EWP-001',1,?,?,?)", id, status, task.thread, task.requestId);
        return task;
    }

    private String statusOf(Task task) {
        return jdbc.queryForObject("select status from ai_tasks where task_id=?", String.class, task.id);
    }

    private static void waitUntil(BooleanSupplier condition, String message) {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return;
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError(message);
    }
}
