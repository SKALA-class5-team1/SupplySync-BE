package com.skala.supplyquote.supplyquote_backend.internal.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.skala.supplyquote.supplyquote_backend.domain.QuoteStatus;
import com.skala.supplyquote.supplyquote_backend.domain.RfqStatus;
import com.skala.supplyquote.supplyquote_backend.dto.QuoteParts;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;

public final class QuoteInternalDto {

    private QuoteInternalDto() {
    }

    /**
     * 6_sg3_quote.json의 quote_draft에서 quote_id, status를 뺀 것 + threadId이다.
     * quote_id는 서버가 생성해서 응답으로 돌려준다.
     *
     * <p>DB에 저장하는 컬럼: modelId, options, unitPriceKrw, partsCostKrw, marginPct, priceByYear, sampleQty,
     * sampleDueDate, validUntil, conditions, checks. 나머지(recipient, item, incoterms, issueDate, eau, sopDate,
     * priceBreakdown, partsCostBreakdown)는 quotes 테이블에 컬럼이 없어 저장하지 않으며, rfqs·카탈로그·발행 시각에서
     * 다시 만들 수 있다. (저장 위치는 데이터셋 담당과 협의 필요)
     */
    @Schema(description = "견적 초안 생성 요청 (SG3). 승인 대기 상태(DRAFT)로 저장된다.")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record QuoteDraftRequest(
            @NotBlank @Schema(description = "어느 분석 실행의 견적인지 (rfq_reviews 연결용)", example = "task-3f2b8c1e-5a4d-4e7b-9c1a-2d6f8e0b7a11") String threadId,
            @NotBlank @Schema(example = "HB-EV7-EWP-001") String rfqNumber,
            @Schema(example = "한별모터스 구매본부") String recipient,
            @Schema(example = "전동식 워터펌프 (EWP, 12V)") String item,
            @NotBlank @Schema(example = "EWP-12-B") String modelId,
            @Schema(example = "[\"OPT-HT\", \"OPT-IP9K\"]") List<String> options,
            @NotNull @Positive @Schema(description = "고객 공개 단가 = 모델가 + 옵션가", example = "37300") Integer unitPriceKrw,
            List<QuoteParts.PriceBreakdown> priceBreakdown,
            @Schema(description = "내부용", example = "21650") Integer partsCostKrw,
            List<QuoteParts.PartCost> partsCostBreakdown,
            @Schema(description = "내부용: 부품 원가 기준 마진(%)", example = "42.0") Double marginPct,
            @NotEmpty @Valid List<QuoteParts.PriceByYear> priceByYear,
            @Schema(example = "120000") Integer eau,
            @Schema(example = "2000") Integer sampleQty,
            @Schema(example = "2026-12-15") LocalDate sampleDueDate,
            @Schema(example = "2027-09-01") LocalDate sopDate,
            @Schema(example = "DAP 한별모터스 울산 2공장") String incoterms,
            @Schema(example = "2026-10-19") LocalDate issueDate,
            @Schema(description = "발행일 + 30일", example = "2026-11-18") LocalDate validUntil,
            List<QuoteParts.Condition> conditions,
            @Schema(description = "내부용: 최종 검토(validate_quote) 결과") List<QuoteParts.Check> checks) {
    }

    @Schema(description = "견적 초안 생성 결과. quote_id는 서버가 `Q-YYYY-MMDD-NNN` 형식으로 생성한다.")
    public record QuoteCreated(
            @Schema(example = "Q-2026-1019-001") String quoteId,
            @Schema(example = "DRAFT") QuoteStatus status,
            @Schema(description = "연결된 rfq_reviews", example = "1") Long reviewId) {
    }

    @Schema(description = "DRAFT 동안 검토 결과와 조건을 갱신한다 (예: validate_quote 재실행). 전달한 필드만 바뀐다.")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record QuotePatchRequest(
            List<QuoteParts.Check> checks,
            List<QuoteParts.Condition> conditions) {
    }

    @Schema(description = "등록 결과. 신규 등록이면 201 CREATED, 이미 등록된 견적이면 200 ALREADY_PROCESSED")
    public record RegisterResponse(
            @Schema(example = "Q-2026-1019-001") String quoteId,
            @Schema(example = "REGISTERED") QuoteStatus status,
            @Schema(example = "QUOTED") RfqStatus rfqStatus,
            @Schema(description = "CREATED | ALREADY_PROCESSED", example = "CREATED") String outcome) {
    }
}
