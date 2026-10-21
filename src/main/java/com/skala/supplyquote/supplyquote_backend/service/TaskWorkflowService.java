package com.skala.supplyquote.supplyquote_backend.service;

import com.skala.supplyquote.supplyquote_backend.common.ApiException;
import com.skala.supplyquote.supplyquote_backend.common.ErrorCode;
import com.skala.supplyquote.supplyquote_backend.auth.CurrentUser;
import com.skala.supplyquote.supplyquote_backend.domain.AgentStep;
import com.skala.supplyquote.supplyquote_backend.domain.ApprovalDecision;
import com.skala.supplyquote.supplyquote_backend.domain.TaskStatus;
import com.skala.supplyquote.supplyquote_backend.internal.dto.EventDto;
import com.skala.supplyquote.supplyquote_backend.messaging.AnalysisDispatch;
import com.skala.supplyquote.supplyquote_backend.web.dto.TaskDto;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

@Service
public class TaskWorkflowService {
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final AnalysisDispatch dispatch;
    private final TaskEventStream stream;
    private final CurrentUser current;

    public TaskWorkflowService(JdbcTemplate jdbc, JsonMapper json, AnalysisDispatch dispatch, TaskEventStream stream,
                               CurrentUser current) {
        this.jdbc = jdbc;
        this.json = json;
        this.dispatch = dispatch;
        this.stream = stream;
        this.current = current;
    }

    public TaskDto.StatusResponse status(UUID id) {
        return jdbc.query("select * from ai_tasks where task_id=?", (rs, row) ->
                new TaskDto.StatusResponse(id, rs.getString("rfq_number"), TaskStatus.valueOf(rs.getString("status")),
                        rs.getString("outcome"),
                        rs.getString("current_step") == null ? null : AgentStep.valueOf(rs.getString("current_step")),
                        rs.getString("failure_reason"), rs.getString("trace_url"),
                        rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()), id)
                .stream().findFirst().orElseThrow(() -> missing(id));
    }

    @Transactional
    public EventDto.EventAccepted event(UUID id, EventDto.EventRequest event) {
        var previous = status(id);
        String outcome = null;
        if (event.type() == com.skala.supplyquote.supplyquote_backend.domain.EventType.RUN_END) {
            Object value = event.data() == null ? null : event.data().get("outcome");
            outcome = value instanceof String text ? text : null;
            if (!"REGISTERED".equals(outcome) && !"REJECTED".equals(outcome))
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "RUN_END에는 data.outcome=REGISTERED 또는 REJECTED가 필요합니다.");
            if (previous.status() == TaskStatus.DONE && outcome.equals(previous.outcome()))
                return new EventDto.EventAccepted(id, TaskStatus.DONE);
            var latestQuote = jdbc.query("""
                    select q.status,q.revision_requested from quotes q join rfq_reviews r on r.review_id=q.review_id
                    where r.thread_id=(select thread_id from ai_tasks where task_id=?)
                    order by q.created_at desc, q.quote_id desc limit 1
                    """, (rs, row) -> Map.entry(rs.getString(1), rs.getBoolean(2)), id)
                    .stream().findFirst().orElse(null);
            if (latestQuote == null || !outcome.equals(latestQuote.getKey())
                    || ("REJECTED".equals(outcome) && latestQuote.getValue()))
                throw conflict("RUN_END 결과와 최신 견적 결정이 일치하지 않습니다.");
        }
        if (previous.status() == TaskStatus.DONE || previous.status() == TaskStatus.FAILED)
            throw conflict("완료된 작업에는 이벤트를 보낼 수 없습니다.");
        TaskStatus next = switch (event.type()) {
            case CLARIFICATION_REQUIRED -> TaskStatus.NEEDS_CLARIFICATION;
            case SAVE_APPROVAL_REQUIRED -> TaskStatus.WAITING_SAVE_APPROVAL;
            case APPROVAL_REQUIRED -> TaskStatus.WAITING_APPROVAL;
            case DONE, RUN_END -> TaskStatus.DONE;
            case FAILED, RUN_FAILED -> TaskStatus.FAILED;
            default -> previous.status() == TaskStatus.QUEUED ? TaskStatus.RUNNING : previous.status();
        };
        jdbc.update("""
                update ai_tasks set status=?,current_step=coalesce(?,current_step),trace_url=coalesce(?,trace_url),
                failure_reason=case when ?='FAILED' then ? else failure_reason end,outcome=coalesce(?,outcome),
                completed_at=case when ? in ('DONE','FAILED') then now() else completed_at end,
                updated_at=now() where task_id=?
                """, next.name(), event.step() == null ? null : event.step().name(), event.traceUrl(),
                next.name(), event.message(), outcome, next.name(), id);
        afterCommit(() -> stream.publish(id, event));
        return new EventDto.EventAccepted(id, next);
    }

    @Transactional
    public TaskDto.ConfirmationAccepted confirm(UUID id, TaskDto.ConfirmationRequest request) {
        long managerUserId = current.requireManagerId();
        var task = status(id);
        if (task.status() != TaskStatus.NEEDS_CLARIFICATION) throw conflict("요구 확인 대기 상태가 아닙니다.");
        String threadId = thread(id);
        if (jdbc.update("update ai_tasks set status='RUNNING',updated_at=now() "
                + "where task_id=? and status='NEEDS_CLARIFICATION'", id) == 0)
            throw conflict("이미 처리된 요구 확인입니다.");
        int updated = jdbc.update("update rfq_reviews set assumptions=?::jsonb,confirmed_by=?,confirmed_at=now(),updated_at=now() where thread_id=?",
                json.writeValueAsString(request.assumptions()), managerUserId, threadId);
        if (updated == 0) throw missing(id);
        dispatch.clarification(id, threadId, managerUserId, request.assumptions());
        return new TaskDto.ConfirmationAccepted(id, TaskStatus.RUNNING);
    }

    @Transactional
    public TaskDto.SaveApprovalAccepted saveApprove(UUID id, TaskDto.SaveApprovalRequest request) {
        long managerUserId = current.requireManagerId();
        var task = status(id);
        if (task.status() != TaskStatus.WAITING_SAVE_APPROVAL) throw conflict("저장 승인 대기 상태가 아닙니다.");
        boolean approved = request.approved();
        if (!approved && (request.reason() == null || request.reason().isBlank()))
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "저장을 거부하려면 reason이 필요합니다.");
        String threadId = thread(id);
        if (jdbc.update("update ai_tasks set status='RUNNING',current_step='QUOTE',updated_at=now() "
                + "where task_id=? and status='WAITING_SAVE_APPROVAL'", id) == 0)
            throw conflict("이미 처리된 저장 승인입니다.");
        dispatch.saveApproval(id, threadId, managerUserId, approved, approved ? null : request.reason());
        return new TaskDto.SaveApprovalAccepted(id, approved, TaskStatus.RUNNING);
    }

    @Transactional
    public TaskDto.ApprovalAccepted approve(UUID id, TaskDto.ApprovalRequest request) {
        long managerUserId = current.requireManagerId();
        var task = status(id);
        if (task.status() != TaskStatus.WAITING_APPROVAL) throw conflict("승인 대기 상태가 아닙니다.");
        if (request.decision() != ApprovalDecision.APPROVED && (request.reason() == null || request.reason().isBlank()))
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "REJECTED/REVISE에는 reason이 필요합니다.");
        String threadId = thread(id);
        var latestQuote = jdbc.query("""
                select q.quote_id,q.revision from quotes q join rfq_reviews r on r.review_id=q.review_id
                where r.thread_id=? order by q.created_at desc,q.quote_id desc limit 1
                """, (rs, row) -> Map.entry(rs.getString(1), rs.getInt(2)), threadId)
                .stream().findFirst().orElseThrow(() -> missing(id));
        String latest = latestQuote.getKey();
        if (!latest.equals(request.quoteId())) throw conflict("최신 견적만 승인·반려·수정 요청할 수 있습니다.");
        if (request.decision() == ApprovalDecision.REVISE && (latestQuote.getValue() < 1 || latestQuote.getValue() >= 4))
            throw conflict("REVISE는 revision 1~3에서만 가능합니다 (수정 최대 3회).");
        boolean approved = request.decision() == ApprovalDecision.APPROVED;
        int updated = jdbc.update("""
                update quotes set status=?,approved_by=?,approved_at=?,reject_reason=?,revision_requested=?
                where quote_id=? and status='DRAFT'
                """, approved ? "APPROVED" : "REJECTED", approved ? managerUserId : null,
                approved ? java.sql.Timestamp.from(Instant.now()) : null, approved ? null : request.reason(),
                request.decision() == ApprovalDecision.REVISE, latest);
        if (updated == 0) throw conflict("이미 처리된 견적입니다.");
        TaskStatus next = approved ? TaskStatus.REVALIDATING : TaskStatus.RUNNING;
        jdbc.update("update ai_tasks set status=?,current_step=?,updated_at=now() where task_id=?",
                next.name(), approved ? "REVALIDATION" : "QUOTE", id);
        Map<String, Object> decision = new HashMap<>();
        decision.put("result", request.decision().name());
        if (!approved) {
            decision.put("reason", request.reason());
        }
        if (request.decision() == ApprovalDecision.REVISE) decision.put("changes", Map.of());
        dispatch.approval(id, threadId, managerUserId, latest, decision);
        return new TaskDto.ApprovalAccepted(id, latest, request.decision(), next);
    }

    private String thread(UUID id) {
        return jdbc.queryForObject("select thread_id from ai_tasks where task_id=?", String.class, id);
    }

    private void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { action.run(); }
        });
    }

    private ApiException missing(UUID id) { return new ApiException(ErrorCode.NOT_FOUND, "없는 작업입니다: " + id); }
    private ApiException conflict(String message) { return new ApiException(ErrorCode.CONFLICT_STATE, message); }
}
