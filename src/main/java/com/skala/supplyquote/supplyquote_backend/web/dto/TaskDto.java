package com.skala.supplyquote.supplyquote_backend.web.dto;

import com.skala.supplyquote.supplyquote_backend.domain.AgentStep;
import com.skala.supplyquote.supplyquote_backend.domain.ApprovalDecision;
import com.skala.supplyquote.supplyquote_backend.domain.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TaskDto {

    private TaskDto() {
    }

    @Schema(description = "분석 작업 상태")
    public record StatusResponse(
            UUID taskId,
            @Schema(example = "HB-EV7-EWP-001") String rfqNumber,
            @Schema(example = "WAITING_APPROVAL") TaskStatus status,
            @Schema(description = "진행 중인 워크플로 단계", example = "APPROVAL") AgentStep currentStep,
            @Schema(description = "FAILED일 때 사유") String failureReason,
            @Schema(description = "LangSmith trace 링크") String traceUrl,
            Instant createdAt,
            Instant updatedAt) {
    }

    @Schema(description = "가정 확인 항목. HIGH 이슈나 누락 값에 대해 담당자가 값을 확정한다.")
    public record AssumptionInput(
            @NotBlank @Schema(example = "spec.flow") String field,
            @NotBlank @Schema(description = "확정할 값", example = "22 L/min") String value) {
    }

    @Schema(description = "요구 확인 요청 (MANAGER 전용)")
    public record ConfirmationRequest(
            @NotEmpty @Valid List<AssumptionInput> assumptions) {
    }

    @Schema(description = "요구 확인 접수 결과. 이후 Agent가 같은 thread로 재개한다.")
    public record ConfirmationAccepted(
            UUID taskId,
            @Schema(example = "RUNNING") TaskStatus status) {
    }

    @Schema(description = "승인/반려 요청 (MANAGER 전용). 승인 단위는 견적(quoteId)이다.")
    public record ApprovalRequest(
            @NotBlank @Schema(example = "Q-2026-1019-001") String quoteId,
            @NotNull @Schema(example = "APPROVED") ApprovalDecision decision,
            @Size(max = 1000) @Schema(description = "반려 사유 (반려 시 필수)", example = "마진 재검토 필요") String reason,
            @Schema(description = "반려 시 되돌아갈 단계. 생략하면 QUOTE", example = "QUOTE") AgentStep returnToStep) {
    }

    @Schema(description = "승인/반려 접수 결과. 이후 재검증·등록은 비동기로 진행된다.")
    public record ApprovalAccepted(
            UUID taskId,
            String quoteId,
            ApprovalDecision decision,
            @Schema(description = "승인이면 REVALIDATING, 반려면 RUNNING(지정 단계로 복귀)", example = "REVALIDATING") TaskStatus status) {
    }
}
