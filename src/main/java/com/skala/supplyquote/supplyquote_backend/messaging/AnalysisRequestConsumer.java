package com.skala.supplyquote.supplyquote_backend.messaging;

import com.skala.supplyquote.supplyquote_backend.messaging.contract.AnalysisRequestedPayload;
import com.skala.supplyquote.supplyquote_backend.messaging.contract.AnalysisResumePayload;
import com.skala.supplyquote.supplyquote_backend.messaging.contract.MessagingContract;
import com.skala.supplyquote.supplyquote_backend.service.AgentGateway;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 브로커 재전달은 processed_messages와 작업 상태로 거르고, 최종 실패는 DLQ로 보낸다. */
@Component
@ConditionalOnProperty(prefix = "app.messaging", name = "enabled", havingValue = "true")
public class AnalysisRequestConsumer {
    private static final Logger log = LoggerFactory.getLogger(AnalysisRequestConsumer.class);
    private static final String CONSUMER = "analysis-request-consumer";
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final AgentGateway agent;

    public AnalysisRequestConsumer(JdbcTemplate jdbc, JsonMapper json, AgentGateway agent) {
        this.jdbc = jdbc;
        this.json = json;
        this.agent = agent;
    }

    @RabbitListener(queues = MessagingContract.QUEUE_ANALYSIS_REQUEST)
    public void receive(Message message) {
        try {
            JsonNode root = json.readTree(message.getBody());
            UUID messageId = UUID.fromString(root.get("message_id").asString());
            UUID requestId = UUID.fromString(root.get("request_id").asString());
            String type = root.get("type").asString();
            if (root.get("schema_version").asInt() != MessagingContract.SCHEMA_VERSION)
                throw new IllegalArgumentException("지원하지 않는 메시지 버전입니다.");
            if (!type.equals(message.getMessageProperties().getReceivedRoutingKey()))
                throw new IllegalArgumentException("메시지 타입과 routing key가 다릅니다.");
            if (alreadyProcessed(messageId)) return;
            switch (type) {
                case MessagingContract.RK_ANALYSIS_REQUESTED -> start(root.get("payload"), requestId);
                case MessagingContract.RK_ANALYSIS_RESUME -> resume(root.get("payload"));
                default -> throw new IllegalArgumentException("알 수 없는 분석 메시지입니다: " + type);
            }
            jdbc.update("insert into processed_messages(message_id,consumer) values (?,?) on conflict do nothing",
                    messageId, CONSUMER);
        } catch (Exception e) {
            log.error("분석 메시지 처리 실패. DLQ로 이동합니다: messageId={}",
                    message.getMessageProperties().getMessageId(), e);
            throw new AmqpRejectAndDontRequeueException("분석 메시지 처리 실패", e);
        }
    }

    private boolean alreadyProcessed(UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from processed_messages where message_id=? and consumer=?)",
                Boolean.class, id, CONSUMER));
    }

    private void start(JsonNode payloadNode, UUID requestId) {
        var payload = json.treeToValue(payloadNode, AnalysisRequestedPayload.class);
        String status = jdbc.queryForObject("select status from ai_tasks where task_id=? and request_id=?",
                String.class, payload.taskId(), requestId);
        if (!"QUEUED".equals(status)) return;
        String rfqNumber = jdbc.queryForObject("select rfq_number from ai_tasks where task_id=?",
                String.class, payload.taskId());
        try {
            retry(() -> agent.startSync(payload.taskId(), payload.rfqId(), rfqNumber,
                    payload.threadId(), requestId));
            jdbc.update("update ai_tasks set status='RUNNING',updated_at=now() where task_id=? and status='QUEUED'",
                    payload.taskId());
        } catch (RuntimeException e) {
            jdbc.update("update ai_tasks set status='FAILED',failure_reason=?,completed_at=now(),updated_at=now() "
                    + "where task_id=? and status='QUEUED'", e.getMessage(), payload.taskId());
            throw e;
        }
    }

    private void resume(JsonNode payloadNode) {
        var payload = json.treeToValue(payloadNode, AnalysisResumePayload.class);
        String status = jdbc.queryForObject("select status from ai_tasks where task_id=?",
                String.class, payload.taskId());
        if ("DONE".equals(status) || "FAILED".equals(status)) return;
        try {
            if (payload.kind() == AnalysisResumePayload.Kind.CLARIFICATION) {
                retry(() -> agent.resumeSync(payload.taskId(), payload.threadId(), "CLARIFICATION", payload.decision()));
            } else {
                retry(() -> agent.resumeApprovalSync(payload.threadId(), payload.userId(), payload.decision()));
            }
        } catch (RuntimeException e) {
            jdbc.update("update ai_tasks set status='FAILED',failure_reason=?,completed_at=now(),updated_at=now() "
                    + "where task_id=? and status in ('RUNNING','REVALIDATING')", e.getMessage(), payload.taskId());
            throw e;
        }
    }

    private void retry(Runnable call) {
        for (int attempt = 0; ; attempt++) {
            try {
                call.run();
                return;
            } catch (RuntimeException e) {
                if (attempt == 3) throw e;
                try {
                    Thread.sleep((1L << attempt) * 1000L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("분석 요청 재시도가 중단됐습니다.", interrupted);
                }
            }
        }
    }
}
