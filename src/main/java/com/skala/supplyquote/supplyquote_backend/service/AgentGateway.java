package com.skala.supplyquote.supplyquote_backend.service;

import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Agent 팀의 HTTP 서버와 통신한다. 주소를 설정하지 않으면 작업은 QUEUED로 남는다. */
@Component
public class AgentGateway {
    private static final Logger log = LoggerFactory.getLogger(AgentGateway.class);
    private final JdbcTemplate jdbc;
    private final RestClient client;
    private final String baseUrl;
    private final String internalApiKey;

    public AgentGateway(JdbcTemplate jdbc,
                        @Value("${app.agent.base-url:}") String baseUrl,
                        @Value("${app.internal-api-key:}") String internalApiKey) {
        this.jdbc = jdbc;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(30_000);
        this.client = RestClient.builder().requestFactory(factory).build();
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.internalApiKey = internalApiKey;
        if (!this.baseUrl.isBlank() && internalApiKey.isBlank())
            throw new IllegalStateException("AGENT_BASE_URL을 사용하려면 INTERNAL_API_KEY가 필요합니다.");
    }

    public void start(UUID taskId, Long rfqId, String rfqNumber, String threadId, UUID requestId) {
        if (baseUrl.isBlank()) return;
        Thread.startVirtualThread(() -> {
            try {
                startSync(taskId, rfqId, rfqNumber, threadId, requestId);
                jdbc.update("update ai_tasks set status='RUNNING', updated_at=now() where task_id=? and status='QUEUED'", taskId);
            } catch (Exception e) {
                log.warn("Agent 실행 요청 실패: taskId={}", taskId, e);
                jdbc.update("update ai_tasks set status='FAILED', failure_reason=?, updated_at=now() where task_id=? and status='QUEUED'",
                        e.getMessage(), taskId);
            }
        });
    }

    /** MQ 소비자용: Agent가 HTTP 요청을 접수해야 반환한다. */
    public void startSync(UUID taskId, Long rfqId, String rfqNumber, String threadId, UUID requestId) {
        requireAgent();
        client.post().uri(baseUrl + "/runs").header("X-Internal-Api-Key", internalApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("task_id", taskId, "rfq_id", rfqId, "rfq_number", rfqNumber,
                        "thread_id", threadId, "request_id", requestId))
                .retrieve().toBodilessEntity();
    }

    public void resume(UUID taskId, String threadId, String kind, Map<String, ?> decision) {
        if (baseUrl.isBlank()) return;
        Thread.startVirtualThread(() -> {
            try {
                resumeSync(taskId, threadId, kind, decision);
            } catch (Exception e) {
                log.warn("Agent 재개 요청 실패: taskId={}", taskId, e);
                jdbc.update("update ai_tasks set status='FAILED', failure_reason=?, updated_at=now() where task_id=?",
                        e.getMessage(), taskId);
            }
        });
    }

    public void resumeSync(UUID taskId, String threadId, String kind, Map<String, ?> decision) {
        requireAgent();
        client.post().uri(baseUrl + "/runs/{threadId}/resume", threadId)
                .header("X-Internal-Api-Key", internalApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("task_id", taskId, "thread_id", threadId, "kind", kind, "decision", decision))
                .retrieve().toBodilessEntity();
    }

    /** SG3 저장 승인 재개 계약: {"approved": true} 또는 {"approved": false, "reason": "..."}. */
    public void resumeSaveApproval(UUID taskId, String threadId, Map<String, ?> decision) {
        if (baseUrl.isBlank()) return;
        Thread.startVirtualThread(() -> {
            try {
                resumeSaveApprovalSync(threadId, decision);
            } catch (Exception e) {
                log.warn("Agent 저장 승인 재개 요청 실패: taskId={}", taskId, e);
                jdbc.update("update ai_tasks set status='FAILED', failure_reason=?, updated_at=now() where task_id=?",
                        e.getMessage(), taskId);
            }
        });
    }

    public void resumeSaveApprovalSync(String threadId, Map<String, ?> decision) {
        requireAgent();
        client.post().uri(baseUrl + "/runs/{threadId}/resume", threadId)
                .header("X-Internal-Api-Key", internalApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(decision)
                .retrieve().toBodilessEntity();
    }

    /** SG4 승인·반려·수정 재개 계약. userId는 로그인한 담당자의 DB ID, quoteId는 결정 대상 견적이다. */
    public void resumeApproval(UUID taskId, String threadId, long userId, String quoteId, Map<String, ?> decision) {
        if (baseUrl.isBlank()) return;
        Thread.startVirtualThread(() -> {
            try {
                resumeApprovalSync(threadId, userId, quoteId, decision);
            } catch (Exception e) {
                log.warn("Agent 승인 재개 요청 실패: taskId={}", taskId, e);
                jdbc.update("update ai_tasks set status='FAILED', failure_reason=?, updated_at=now() where task_id=?",
                        e.getMessage(), taskId);
            }
        });
    }

    public void resumeApprovalSync(String threadId, long userId, String quoteId, Map<String, ?> decision) {
        requireAgent();
        client.post().uri(baseUrl + "/runs/{threadId}/resume", threadId)
                .header("X-Internal-Api-Key", internalApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(quoteId == null
                        ? Map.of("step", "SG4.request_approval", "userId", userId, "decision", decision)
                        : Map.of("step", "SG4.request_approval", "userId", userId, "quoteId", quoteId,
                                "decision", decision))
                .retrieve().toBodilessEntity();
    }

    private void requireAgent() {
        if (baseUrl.isBlank()) throw new IllegalStateException("AGENT_BASE_URL이 설정되지 않았습니다.");
    }
}
