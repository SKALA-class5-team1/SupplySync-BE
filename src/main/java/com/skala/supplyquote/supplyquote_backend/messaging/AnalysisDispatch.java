package com.skala.supplyquote.supplyquote_backend.messaging;

import com.skala.supplyquote.supplyquote_backend.messaging.contract.AnalysisRequestedPayload;
import com.skala.supplyquote.supplyquote_backend.messaging.contract.AnalysisResumePayload;
import com.skala.supplyquote.supplyquote_backend.messaging.contract.MessageEnvelope;
import com.skala.supplyquote.supplyquote_backend.messaging.contract.MessagingContract;
import com.skala.supplyquote.supplyquote_backend.service.AgentGateway;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** 업무 트랜잭션 안에서 발행할 명령을 기록한다. MQ가 꺼져 있으면 기존 HTTP 어댑터를 사용한다. */
@Component
public class AnalysisDispatch {
    private final boolean enabled;
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final AgentGateway agent;

    public AnalysisDispatch(@Value("${app.messaging.enabled:false}") boolean enabled,
                            JdbcTemplate jdbc, JsonMapper json, AgentGateway agent) {
        this.enabled = enabled;
        this.jdbc = jdbc;
        this.json = json;
        this.agent = agent;
    }

    public void requested(UUID taskId, Long rfqId, Long requestedBy, String rfqNumber,
                          String threadId, UUID requestId) {
        if (!enabled) {
            afterCommit(() -> agent.start(taskId, rfqId, rfqNumber, threadId, requestId));
            return;
        }
        enqueue(MessageEnvelope.of(requestId, MessagingContract.RK_ANALYSIS_REQUESTED,
                new AnalysisRequestedPayload(taskId, rfqId, requestedBy, threadId)));
    }

    public void clarification(UUID taskId, String threadId, long userId, Object assumptions) {
        if (!enabled) {
            afterCommit(() -> agent.resume(taskId, threadId, "CLARIFICATION", Map.of("assumptions", assumptions)));
            return;
        }
        enqueue(MessageEnvelope.of(UUID.randomUUID(), MessagingContract.RK_ANALYSIS_RESUME,
                new AnalysisResumePayload(taskId, null, threadId, AnalysisResumePayload.Kind.CLARIFICATION,
                        userId, Map.of("assumptions", assumptions))));
    }

    public void approval(UUID taskId, String threadId, long userId, Map<String, Object> decision) {
        if (!enabled) {
            afterCommit(() -> agent.resumeApproval(taskId, threadId, userId, decision));
            return;
        }
        var kind = AnalysisResumePayload.Kind.valueOf((String) decision.get("result"));
        enqueue(MessageEnvelope.of(UUID.randomUUID(), MessagingContract.RK_ANALYSIS_RESUME,
                new AnalysisResumePayload(taskId, null, threadId, kind, userId, decision)));
    }

    private void enqueue(MessageEnvelope<?> envelope) {
        jdbc.update("insert into analysis_outbox(message_id,request_id,type,body) values (?,?,?,?::jsonb)",
                envelope.messageId(), envelope.requestId(), envelope.type(), json.writeValueAsString(envelope));
    }

    private void afterCommit(Runnable action) {
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCommit() { action.run(); }
                });
    }
}
