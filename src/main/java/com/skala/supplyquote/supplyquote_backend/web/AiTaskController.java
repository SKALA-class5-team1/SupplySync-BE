package com.skala.supplyquote.supplyquote_backend.web;

import com.skala.supplyquote.supplyquote_backend.common.ErrorResponse;
import com.skala.supplyquote.supplyquote_backend.domain.ApprovalDecision;
import com.skala.supplyquote.supplyquote_backend.domain.TaskStatus;
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

@Tag(name = "AI 작업", description = "분석 작업 상태, 진행 이벤트(SSE), 승인 (화면용)")
@RestController
@RequestMapping("/api/ai/tasks")
public class AiTaskController {

    @Operation(summary = "작업 상태 조회", description = "[STUB]")
    @ApiResponse(responseCode = "404", description = "없는 작업",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{taskId}")
    public TaskDto.StatusResponse status(@PathVariable UUID taskId) {
        return new TaskDto.StatusResponse(taskId, 101L, TaskStatus.WAITING_APPROVAL, "승인 대기", null, null,
                Instant.parse("2026-10-19T06:00:00Z"), Instant.parse("2026-10-19T06:03:00Z"));
    }

    @Operation(summary = "진행 이벤트 구독 (SSE)",
            description = """
                    [STUB] `text/event-stream`. 이벤트 이름(event)과 의미:

                    | event | 언제 | 화면 표현 |
                    | --- | --- | --- |
                    | `TOKEN` | 답변·제안서가 생성되는 동안 | 답변 영역에 이어서 표시 |
                    | `TOOL_START`, `TOOL_END` | MCP 도구 실행 전후 | "재고 조회 중…" 상태 표시 |
                    | `CLARIFICATION_REQUIRED` | HIGH 이슈(예: 유량 단위 오기)로 멈췄을 때. data에 `issues` 목록 | 요구 확인 카드 |
                    | `APPROVAL_REQUIRED` | 승인 대기로 멈췄을 때 | 승인 카드 |
                    | `DONE`, `FAILED` | 작업이 끝났을 때 | 스트림을 닫고 목록 갱신 |

                    data는 JSON이며 공통 필드는 `taskId`, `step`, `at`이다.""")
    @ApiResponse(responseCode = "200", description = "SSE 스트림",
            content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE, schema = @Schema(type = "string")))
    @GetMapping(value = "/{taskId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable UUID taskId) {
        SseEmitter emitter = new SseEmitter(60_000L);
        Thread.startVirtualThread(() -> {
            try {
                emit(emitter, "TOOL_START", taskId, "재고 조회", Map.of("tool", "business_get_inventory"));
                Thread.sleep(300);
                emit(emitter, "TOOL_END", taskId, "재고 조회", Map.of("tool", "business_get_inventory"));
                Thread.sleep(300);
                emit(emitter, "APPROVAL_REQUIRED", taskId, "승인 대기", Map.of("resultVersion", 1));
                emit(emitter, "DONE", taskId, "완료", Map.of());
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    private static void emit(SseEmitter emitter, String event, UUID taskId, String step, Map<String, Object> extra)
            throws IOException {
        var data = new LinkedHashMap<String, Object>();
        data.put("taskId", taskId);
        data.put("step", step);
        data.put("at", Instant.now());
        data.putAll(extra);
        emitter.send(SseEmitter.event().name(event).data(data));
    }

    @Operation(summary = "요구 확인 (가정 승인)",
            description = "[STUB] 영업 담당자 전용. 작업이 NEEDS_CLARIFICATION일 때, HIGH 이슈나 누락 값에 대해 확정한 값을 보내면 "
                    + "Agent가 같은 thread로 재개한다. 예: `spec.flow = 22 L/min`. 작업이 확인 대기 상태가 아니면 409.")
    @ApiResponse(responseCode = "202", description = "접수됨")
    @ApiResponse(responseCode = "403", description = "담당자가 아님",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "확인 대기 상태가 아님",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/{taskId}/confirmation")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TaskDto.ConfirmationAccepted confirm(@PathVariable UUID taskId,
                                                @Valid @RequestBody TaskDto.ConfirmationRequest request) {
        return new TaskDto.ConfirmationAccepted(taskId, TaskStatus.RUNNING);
    }

    @Operation(summary = "승인/반려",
            description = "[STUB] 담당자 전용. 승인이면 최신 조건 재검증 후 등록이 이어지고(REVALIDATING), 반려면 해당 단계로 돌아가 재분석한다. "
                    + "resultVersion이 최신 분석 결과와 다르면 409.")
    @ApiResponse(responseCode = "202", description = "접수됨")
    @ApiResponse(responseCode = "403", description = "담당자가 아님",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "STALE_VERSION: 이미 새 버전이 있거나 승인 대기 상태가 아님",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/{taskId}/approval")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TaskDto.ApprovalAccepted approve(@PathVariable UUID taskId, @Valid @RequestBody TaskDto.ApprovalRequest request) {
        TaskStatus next = request.decision() == ApprovalDecision.APPROVED
                ? TaskStatus.REVALIDATING : TaskStatus.RUNNING;
        return new TaskDto.ApprovalAccepted(taskId, request.decision(), next);
    }
}
