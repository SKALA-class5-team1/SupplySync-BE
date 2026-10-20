package com.skala.supplyquote.supplyquote_backend.web;

import com.skala.supplyquote.supplyquote_backend.common.ApiException;
import com.skala.supplyquote.supplyquote_backend.common.ErrorCode;
import com.skala.supplyquote.supplyquote_backend.common.ErrorResponse;
import com.skala.supplyquote.supplyquote_backend.domain.AgentStep;
import com.skala.supplyquote.supplyquote_backend.domain.ApprovalDecision;
import com.skala.supplyquote.supplyquote_backend.domain.TaskStatus;
import com.skala.supplyquote.supplyquote_backend.stub.StubData;
import com.skala.supplyquote.supplyquote_backend.web.dto.TaskDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
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

    @Operation(summary = "작업 상태 조회",
            description = "[STUB] SSE 연결이 끊긴 뒤 현재 상태를 복구하는 데도 쓴다. CUSTOMER는 자기 RFQ의 작업만 보며 current_step 수준의 거친 상태만 받는다.")
    @ApiResponse(responseCode = "404", description = "없는 작업",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{taskId}")
    public TaskDto.StatusResponse status(@PathVariable UUID taskId) {
        return new TaskDto.StatusResponse(taskId, StubData.RFQ_NUMBER, TaskStatus.WAITING_APPROVAL, AgentStep.APPROVAL,
                null, null, Instant.parse("2026-10-19T01:00:00Z"), Instant.parse("2026-10-19T01:50:00Z"));
    }

    @Operation(summary = "진행 이벤트 구독 (SSE)",
            description = """
                    [STUB] `text/event-stream`. 이벤트 이름(event)과 의미:

                    | event | 언제 | 화면 표현 |
                    | --- | --- | --- |
                    | `STEP_START`, `STEP_END` | Agent 워크플로 단계 시작·종료. data.step = RFQ_ANALYSIS, SPEC_MATCH, SUPPLY_CHECK, ALTERNATIVE_ANALYSIS(조건부), QUOTE, FINAL_REVIEW, APPROVAL, REVALIDATION, REGISTRATION | 진행 표시줄 |
                    | `TOOL_START`, `TOOL_END` | MCP 도구 실행 전후 | "재고 조회 중…" |
                    | `TOKEN` | 설명·제안서 생성 중 | 이어서 표시 |
                    | `CLARIFICATION_REQUIRED` | HIGH 이슈로 멈춤. data.issues 포함. **MANAGER에게만 전송** | 요구 확인 카드 |
                    | `APPROVAL_REQUIRED` | 견적 초안과 최종 검토 완료. data.quote_id | 승인 카드 |
                    | `DONE`, `FAILED` | 작업 종료 | 스트림 닫고 목록 갱신 |

                    data는 JSON이며 공통 필드는 `task_id`, `step`, `at`이다. CUSTOMER 구독자에게는 STEP_*, DONE, FAILED만 보낸다.""")
    @ApiResponse(responseCode = "200", description = "SSE 스트림",
            content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE, schema = @Schema(type = "string")))
    @GetMapping(value = "/{taskId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable UUID taskId) {
        SseEmitter emitter = new SseEmitter(60_000L);
        Thread.startVirtualThread(() -> {
            try {
                emit(emitter, "STEP_START", taskId, AgentStep.SUPPLY_CHECK, Map.of());
                Thread.sleep(300);
                emit(emitter, "TOOL_START", taskId, AgentStep.SUPPLY_CHECK, Map.of("tool", "get_inventory"));
                emit(emitter, "TOOL_END", taskId, AgentStep.SUPPLY_CHECK, Map.of("tool", "get_inventory"));
                emit(emitter, "STEP_END", taskId, AgentStep.SUPPLY_CHECK, Map.of());
                Thread.sleep(300);
                emit(emitter, "APPROVAL_REQUIRED", taskId, AgentStep.APPROVAL, Map.of("quote_id", StubData.QUOTE_ID));
                emit(emitter, "DONE", taskId, AgentStep.REGISTRATION, Map.of());
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    private static void emit(SseEmitter emitter, String event, UUID taskId, AgentStep step, Map<String, Object> extra)
            throws IOException {
        var data = new LinkedHashMap<String, Object>();
        data.put("task_id", taskId);
        data.put("step", step);
        data.put("at", Instant.now());
        data.putAll(extra);
        emitter.send(SseEmitter.event().name(event).data(data));
    }

    @Operation(summary = "요구 확인 (가정 승인)",
            description = "[STUB] **MANAGER 전용.** 작업이 NEEDS_CLARIFICATION일 때, HIGH 이슈나 누락 값에 대해 확정한 값을 보내면 "
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
        return new TaskDto.ConfirmationAccepted(taskId, TaskStatus.RUNNING);
    }

    @Operation(summary = "견적 승인/반려",
            description = "[STUB] **MANAGER 전용.** 승인 단위는 견적(quote_id)이다. 승인이면 견적을 APPROVED로 바꾸고 최신 조건 재검증 후 등록이 이어진다(작업 REVALIDATING). "
                    + "반려면 reason(필수)을 저장하고 return_to_step(기본 QUOTE)으로 돌아가 견적을 다시 만든다 (새 quote_id의 DRAFT가 생긴다). "
                    + "견적이 DRAFT가 아니거나 최신 견적이 아니면 409 — 승인 버튼 더블 클릭도 여기서 걸러진다.")
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
        boolean approved = request.decision() == ApprovalDecision.APPROVED;
        if (!approved && (request.reason() == null || request.reason().isBlank())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "반려 사유(reason)가 필요합니다.");
        }
        return new TaskDto.ApprovalAccepted(taskId, request.quoteId(), request.decision(),
                approved ? TaskStatus.REVALIDATING : TaskStatus.RUNNING);
    }
}
