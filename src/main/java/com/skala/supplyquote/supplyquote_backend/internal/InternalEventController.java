package com.skala.supplyquote.supplyquote_backend.internal;

import com.skala.supplyquote.supplyquote_backend.internal.dto.EventDto;
import com.skala.supplyquote.supplyquote_backend.internal.dto.QuoteInternalDto;
import com.skala.supplyquote.supplyquote_backend.web.dto.TaskDto;
import com.skala.supplyquote.supplyquote_backend.service.TaskWorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "내부 - 진행 이벤트", description = "Agent용: 진행 이벤트 전달. 내부 API 인증은 다음 단계에서 적용한다.")
@RestController
@RequestMapping("/internal/tasks")
public class InternalEventController {
    private final TaskWorkflowService tasks;

    public InternalEventController(TaskWorkflowService tasks) { this.tasks = tasks; }

    @Operation(summary = "견적 초안 임시 저장 (SG3 저장 승인용)",
            description = "SG3가 저장 승인을 기다리기 **전에** 보여 줄 견적 초안을 올린다. 본문은 `POST /internal/quotes`와 같다. "
                    + "정식 견적(quotes, quote_id)은 만들지 않고 작업에 임시로 붙이며 MANAGER만 `GET /api/ai/tasks/{id}`의 `pending_draft`로 본다. "
                    + "같은 작업에 다시 보내면 덮어쓴다. 담당자가 저장을 승인하거나 거부하면 비워진다. "
                    + "진행 중(RUNNING)이거나 저장 승인 대기 중인 작업이 아니면 409, thread_id·rfq_number가 작업과 다르면 400.")
    @PutMapping("/{taskId}/draft")
    public TaskDto.DraftAccepted draft(@PathVariable UUID taskId, @Valid @RequestBody QuoteInternalDto.QuoteDraftRequest request) {
        return tasks.saveDraft(taskId, request);
    }

    @Operation(summary = "진행 이벤트 전달",
            description = "Agent가 단계 시작·종료, 도구 실행, 사람 대기를 알린다. Spring은 작업 상태(ai_tasks.status, current_step)를 갱신하고 열려 있는 SSE 연결로 중계한다. "
                    + "CLARIFICATION_REQUIRED → NEEDS_CLARIFICATION, APPROVAL_REQUIRED → WAITING_APPROVAL, "
                    + "RUN_END(data.outcome=REGISTERED|REJECTED) → DONE, RUN_FAILED → FAILED로 전이한다. "
                    + "TOKEN처럼 빈도가 높은 이벤트는 큐(RabbitMQ)를 거치지 않고 이 HTTP로 직접 보낸다. 고객 구독자에게는 이슈 내용이 담긴 이벤트를 중계하지 않는다.")
    @PostMapping("/{taskId}/events")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public EventDto.EventAccepted event(@PathVariable UUID taskId, @Valid @RequestBody EventDto.EventRequest request) {
        return tasks.event(taskId, request);
    }
}
