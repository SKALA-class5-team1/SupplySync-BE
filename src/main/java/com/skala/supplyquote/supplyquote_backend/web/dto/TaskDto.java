package com.skala.supplyquote.supplyquote_backend.web.dto;

import com.skala.supplyquote.supplyquote_backend.domain.AgentStep;
import com.skala.supplyquote.supplyquote_backend.domain.ApprovalDecision;
import com.skala.supplyquote.supplyquote_backend.domain.TaskStatus;
import com.skala.supplyquote.supplyquote_backend.internal.dto.QuoteInternalDto;
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
            @Schema(description = "종료 결과: REGISTERED 또는 REJECTED. 진행 중에는 null") String outcome,
            @Schema(description = "진행 중인 워크플로 단계", example = "APPROVAL") AgentStep currentStep,
            @Schema(description = "FAILED일 때 사유") String failureReason,
            @Schema(description = "LangSmith trace 링크") String traceUrl,
            Instant createdAt,
            Instant updatedAt,
            @Schema(description = "SG3 저장 승인 대기 중인 견적 초안. MANAGER에게만 내려가며 그 외에는 null") QuoteInternalDto.QuoteDraftRequest pendingDraft) {
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

    @Schema(description = "승인/종료 반려/수정 요청 (MANAGER 전용). 승인 단위는 견적(quoteId)이다.")
    public record ApprovalRequest(
            @NotBlank @Schema(example = "Q-2026-1019-001") String quoteId,
            @NotNull @Schema(example = "APPROVED") ApprovalDecision decision,
            @Size(max = 1000) @Schema(description = "REJECTED/REVISE 시 필수 사유", example = "보증 조건 문구 수정") String reason) {
    }

    @Schema(description = "임시 초안 저장 결과")
    public record DraftAccepted(
            UUID taskId,
            @Schema(example = "WAITING_SAVE_APPROVAL") TaskStatus status) {
    }

    @Schema(description = "SG3 저장 승인 요청 (MANAGER 전용). 견적을 저장하기 전에 APPROVED(저장) 또는 REVISE(사유를 반영해 재작성)를 고른다. REJECTED는 저장된 견적에 대한 SG4 단계에서만 가능하다.")
    public record SaveApprovalRequest(
            @NotNull @Schema(example = "APPROVED") ApprovalDecision decision,
            @Size(max = 1000) @Schema(description = "REVISE 시 필수 사유", example = "마진을 다시 계산해 주세요") String reason) {
    }

    @Schema(description = "SG3 저장 승인 접수 결과. 이후 Agent가 같은 thread로 재개한다.")
    public record SaveApprovalAccepted(
            UUID taskId,
            ApprovalDecision decision,
            @Schema(example = "RUNNING") TaskStatus status) {
    }

    @Schema(description = "승인/종료 반려/수정 접수 결과. 이후 Agent가 비동기로 처리한다.")
    public record ApprovalAccepted(
            UUID taskId,
            String quoteId,
            ApprovalDecision decision,
            @Schema(description = "APPROVED면 REVALIDATING, REJECTED/REVISE면 RUNNING", example = "REVALIDATING") TaskStatus status) {
    }
}
