package com.skala.supplyquote.supplyquote_backend.web;

import com.skala.supplyquote.supplyquote_backend.common.ErrorResponse;
import com.skala.supplyquote.supplyquote_backend.auth.CurrentUser;
import com.skala.supplyquote.supplyquote_backend.domain.UserType;
import com.skala.supplyquote.supplyquote_backend.service.TaskWorkflowService;
import com.skala.supplyquote.supplyquote_backend.service.TaskEventStream;
import com.skala.supplyquote.supplyquote_backend.web.dto.TaskDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "AI 작업", description = "분석 작업 상태, 진행 이벤트(SSE), 요구 확인, 견적 승인 (화면용)")
@RestController
@RequestMapping("/api/ai/tasks")
public class AiTaskController {
    private final TaskWorkflowService tasks;
    private final TaskEventStream events;
    private final CurrentUser current;

    public AiTaskController(TaskWorkflowService tasks, TaskEventStream events, CurrentUser current) {
        this.tasks = tasks;
        this.events = events;
        this.current = current;
    }

    @Operation(summary = "작업 상태 조회",
            description = "DB의 현재 작업 상태를 반환한다. SSE 연결이 끊긴 뒤 상태 복구에도 사용한다. 고객은 소속 회사 작업만 볼 수 있다.")
    @ApiResponse(responseCode = "404", description = "없는 작업",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{taskId}")
    public TaskDto.StatusResponse status(@PathVariable UUID taskId) {
        current.requireTask(taskId);
        var result = tasks.status(taskId);
        if (current.get().userType() == UserType.MANAGER) return result;
        return new TaskDto.StatusResponse(result.taskId(), result.rfqNumber(), result.status(), result.outcome(),
                result.currentStep(), null, null, result.createdAt(), result.updatedAt(), null);
    }

    @Operation(summary = "진행 이벤트 구독 (SSE)",
            description = """
                    `text/event-stream`. Bearer 토큰을 붙여 구독한다. Agent 이벤트를 실시간 중계하고 재연결 시 작업 상태 API를 조회한다. 고객에게는 단계·시간만 전달한다.

                    | event | 언제 | 화면 표현 |
                    | --- | --- | --- |
                    | `STEP_START`, `STEP_END` | Agent 워크플로 단계 시작·종료. data.step = RFQ_ANALYSIS, SPEC_MATCH, SUPPLY_CHECK, ALTERNATIVE_ANALYSIS(조건부), QUOTE, FINAL_REVIEW, APPROVAL, REVALIDATION, REGISTRATION | 진행 표시줄 |
                    | `TOOL_START`, `TOOL_END` | MCP 도구 실행 전후 | "재고 조회 중…" |
                    | `TOKEN` | 설명·제안서 생성 중 | 이어서 표시 |
                    | `CLARIFICATION_REQUIRED` | HIGH 이슈로 멈춤. **MANAGER에게만 전송** | `/api/rfqs/{id}/analysis`를 다시 조회해 요구 확인 카드 표시 |
                    | `SAVE_APPROVAL_REQUIRED` | SG3가 견적을 저장하기 직전 대기. **MANAGER에게만 전송** | 저장 승인 카드 표시 (`POST .../save-approval`) |
                    | `APPROVAL_REQUIRED` | 견적 초안과 최종 검토 완료. **MANAGER에게만 전송** | `/api/rfqs/{id}/analysis`를 다시 조회해 승인 카드 표시 |
                    | `RUN_END`, `RUN_FAILED` | Agent 작업 종료 | 스트림 닫고 목록 갱신 |

                    data는 JSON이며 공통 필드는 `task_id`, `step`, `at`이다. 담당자에게만 메시지를 전달한다. 이슈·견적 상세는 이벤트 본문에 없으므로 분석 API를 조회한다.""")
    @ApiResponse(responseCode = "200", description = "SSE 스트림",
            content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE, schema = @Schema(type = "string")))
    @GetMapping(value = "/{taskId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable UUID taskId) {
        current.requireTask(taskId);
        tasks.status(taskId);
        return events.subscribe(taskId, current.get().userType(), Math.min(current.tokenRemainingMillis(), 300_000L));
    }

    @Operation(summary = "요구 확인 (가정 승인)",
            description = "작업이 NEEDS_CLARIFICATION일 때 확정한 가정값을 저장하고 Agent 재개 HTTP를 호출한다. 로그인한 담당자 ID를 사용한다. "
                    + "가정(assumptions)과 확인자(confirmed_by)를 저장하고 Agent가 같은 thread로 재개한다. 예: `spec.flow = 22 L/min`. "
                    + "확인 대기 상태가 아니면 409.")
    @ApiResponse(responseCode = "202", description = "접수됨")
    @ApiResponse(responseCode = "403", description = "MANAGER가 아님",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "확인 대기 상태가 아님",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/{taskId}/confirmation")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TaskDto.ConfirmationAccepted confirm(@PathVariable UUID taskId,
                                                @Valid @RequestBody TaskDto.ConfirmationRequest request) {
        return tasks.confirm(taskId, request);
    }

    @Operation(summary = "견적 저장 승인 (SG3)",
            description = "작업이 WAITING_SAVE_APPROVAL(Agent의 SAVE_APPROVAL_REQUIRED)일 때 SG3의 견적 저장 여부를 정한다. "
                    + "decision은 APPROVED(저장) 또는 REVISE(사유를 반영해 재작성)이고 REJECTED는 400이다. REVISE에는 reason이 필수이며 "
                    + "Agent에는 {step: SG3.request_save_approval, userId, decision:{result, reason, changes}}로 재개한다. "
                    + "저장 승인 대기 상태가 아니면 409.")
    @ApiResponse(responseCode = "202", description = "접수됨")
    @ApiResponse(responseCode = "400", description = "REJECTED이거나 REVISE인데 reason이 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "MANAGER가 아님",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "저장 승인 대기 상태가 아님",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/{taskId}/save-approval")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TaskDto.SaveApprovalAccepted saveApprove(@PathVariable UUID taskId,
                                                    @Valid @RequestBody TaskDto.SaveApprovalRequest request) {
        return tasks.saveApprove(taskId, request);
    }

    @Operation(summary = "견적 승인/종료 반려/수정 요청",
            description = "APPROVED면 재고 재확인·등록, REJECTED면 사유 저장 후 RUN_END로 종료, REVISE면 사유 저장 후 SG3에서 새 견적을 만든다. "
                    + "REJECTED와 REVISE는 reason이 필수이며 REVISE는 최대 3회다. 견적이 DRAFT가 아니거나 최신 견적이 아니면 409다.")
    @ApiResponse(responseCode = "202", description = "접수됨")
    @ApiResponse(responseCode = "400", description = "반려인데 reason이 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "MANAGER가 아님",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "CONFLICT_STATE: 이미 처리됐거나 승인 대기 상태가 아님",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/{taskId}/approval")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TaskDto.ApprovalAccepted approve(@PathVariable UUID taskId, @Valid @RequestBody TaskDto.ApprovalRequest request) {
        return tasks.approve(taskId, request);
    }
}
