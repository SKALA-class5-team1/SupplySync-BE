package com.skala.supplyquote.supplyquote_backend.internal.dto;

import com.skala.supplyquote.supplyquote_backend.web.dto.AnalysisDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 견적·구매요청 등록과 SG1 검토 결과 기록 계약.
 * AI가 만든 requestId를 두 단계(QUOTE, PURCHASE_REQUEST)에 같은 값으로 보낸다.
 * 이미 성공한 단계는 다시 저장하지 않고 ALREADY_PROCESSED로 응답하므로, 재시도하면 실패한 단계만 처리된다.
 */
public final class RegistrationDto {

    private RegistrationDto() {
    }

    @Schema(description = "CREATED: 이번 요청으로 새로 저장 / ALREADY_PROCESSED: 같은 requestId로 이미 처리됨(저장하지 않음)")
    public enum Outcome {
        CREATED,
        ALREADY_PROCESSED
    }

    @Schema(description = "등록 단계")
    public enum Step {
        QUOTE,
        PURCHASE_REQUEST
    }

    @Schema(description = "견적 라인. modelId나 partId 중 하나를 쓰고, 둘 다 없으면 description만 저장한다.")
    public record QuoteLine(
            @Schema(example = "EWP-12-B") String modelId,
            @Schema(example = "P-MCU-G1-ALT") String partId,
            @NotBlank @Schema(example = "중형 워터펌프 EWP-12-B + OPT-HT + OPT-IP9K (시작품)") String description,
            @NotNull @Positive @Schema(example = "2000") BigDecimal quantity,
            @NotNull @Schema(example = "37300") BigDecimal unitPrice) {
    }

    @Schema(description = "견적 등록 요청 (business MCP → Spring)")
    public record QuoteRequest(
            @NotNull @Schema(description = "AI가 생성한 멱등 키") UUID requestId,
            @NotNull Long analysisResultId,
            @NotNull @Schema(description = "승인받은 결과 버전. 최신 버전과 다르면 409 STALE_VERSION", example = "1") Integer analysisResultVersion,
            @NotNull @Positive @Schema(example = "74750000") BigDecimal totalPrice,
            @Schema(description = "제안 납품일", example = "2026-12-15") LocalDate deliveryDate,
            @NotEmpty @Valid List<QuoteLine> items) {
    }

    @Schema(description = "구매요청 라인")
    public record PurchaseLine(
            @NotBlank @Schema(example = "P-MCU-G1-ALT") String partId,
            @NotBlank @Schema(description = "parts.supplier와 같은 값", example = "누리칩스") String supplier,
            @NotNull @Positive @Schema(example = "500") Integer quantity,
            @NotNull @Schema(example = "5600") BigDecimal unitPrice,
            @Schema(example = "2026-11-20") LocalDate expectedArrivalDate) {
    }

    @Schema(description = "구매요청 등록 요청 (business MCP → Spring)")
    public record PurchaseRequestRequest(
            @NotNull @Schema(description = "견적 등록과 같은 requestId를 쓴다") UUID requestId,
            @NotNull Long analysisResultId,
            @NotNull Integer analysisResultVersion,
            @NotNull @Schema(description = "먼저 등록된 견적") Long quoteId,
            @NotEmpty @Valid List<PurchaseLine> items) {
    }

    @Schema(description = "등록 결과. 신규면 201, 이미 처리됨이면 200으로 응답한다.")
    public record Response(
            UUID requestId,
            Step step,
            Outcome outcome,
            @Schema(description = "생성된(또는 이미 존재하는) quote/purchaseRequest id", example = "55") Long resourceId,
            Instant registeredAt) {
    }

    @Schema(description = "requestId의 단계별 처리 현황. 재시도 전에 어느 단계가 실패했는지 확인한다.")
    public record StepStatus(
            Step step,
            @Schema(description = "SUCCEEDED | FAILED", example = "FAILED") String status,
            Long resourceId,
            String errorMessage,
            Instant updatedAt) {
    }

    @Schema(description = "SG1 검토 결과 기록 요청 (rfq_reviews). 같은 rfqId로 다시 호출하면 이력이 한 행 더 쌓인다.")
    public record RfqReviewRequest(
            @NotBlank @Schema(description = "요청서의 RFQ 번호", example = "HB-EV7-EWP-001") String rfqId,
            @Schema(example = "EWP-12-B") String selectedModel,
            @Schema(example = "[\"OPT-HT\", \"OPT-IP9K\"]") List<String> options,
            @Schema(description = "표준 | 파생 사양 | 신규 개발", example = "파생 사양") String fitType,
            @Schema(description = "검증 이슈 목록 (정답 7건)") List<AnalysisDto.Issue> issues) {
    }

    @Schema(description = "SG1 검토 결과 기록 응답")
    public record RfqReviewResponse(
            @Schema(example = "1") Integer reviewId,
            Instant reviewedAt) {
    }
}
