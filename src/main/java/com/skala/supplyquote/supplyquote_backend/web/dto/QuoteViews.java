package com.skala.supplyquote.supplyquote_backend.web.dto;

import com.skala.supplyquote.supplyquote_backend.domain.QuoteStatus;
import com.skala.supplyquote.supplyquote_backend.dto.QuoteParts;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 견적의 역할별 응답. 필드를 null로 숨기지 않고 **클래스를 분리**해서 내부용 필드가 실수로 노출되지 않게 한다.
 * 데이터셋 규칙: 고객은 REGISTERED 견적만 보고, parts_cost · margin · checks는 내부용이다.
 */
public final class QuoteViews {

    private QuoteViews() {
    }

    @Schema(description = "담당자용 견적. 내부 필드(원가, 마진, 검토 결과)를 포함한다.")
    public record ManagerQuote(
            @Schema(example = "Q-2026-1019-001") String quoteId,
            @Schema(example = "HB-EV7-EWP-001") String rfqNumber,
            @Schema(example = "EWP-12-B") String modelId,
            @Schema(example = "[\"OPT-HT\", \"OPT-IP9K\"]") List<String> options,
            @Schema(description = "고객 공개 단가", example = "37300") Integer unitPriceKrw,
            @Schema(description = "내부용: 옵션 반영 BOM 부품 원가 합계", example = "21650") Integer partsCostKrw,
            @Schema(description = "내부용: 부품 원가 기준 마진(%)", example = "42.0") Double marginPct,
            List<QuoteParts.PriceByYear> priceByYear,
            @Schema(example = "2000") Integer sampleQty,
            @Schema(example = "2026-12-15") LocalDate sampleDueDate,
            @Schema(description = "발행일 + 30일", example = "2026-11-18") LocalDate validUntil,
            List<QuoteParts.Condition> conditions,
            @Schema(description = "내부용: 최종 검토 결과") List<QuoteParts.Check> checks,
            @Schema(example = "DRAFT") QuoteStatus status,
            @Schema(description = "승인한 담당자(users.user_id)", example = "2") Long approvedBy,
            LocalDateTime approvedAt,
            String rejectReason,
            LocalDateTime registeredAt,
            LocalDateTime createdAt) {
    }

    @Schema(description = "고객용 견적. REGISTERED 견적만 조회되며 원가·마진·검토 결과가 없다.")
    public record CustomerQuote(
            @Schema(example = "Q-2026-1019-001") String quoteId,
            @Schema(example = "HB-EV7-EWP-001") String rfqNumber,
            @Schema(example = "EWP-12-B") String modelId,
            @Schema(example = "[\"OPT-HT\", \"OPT-IP9K\"]") List<String> options,
            @Schema(example = "37300") Integer unitPriceKrw,
            @Schema(description = "연도별 단가 (마진 제외)") List<QuoteParts.CustomerPriceByYear> priceByYear,
            @Schema(example = "2000") Integer sampleQty,
            @Schema(example = "2026-12-15") LocalDate sampleDueDate,
            @Schema(example = "2026-11-18") LocalDate validUntil,
            List<QuoteParts.Condition> conditions,
            @Schema(example = "REGISTERED") QuoteStatus status) {
    }
}
