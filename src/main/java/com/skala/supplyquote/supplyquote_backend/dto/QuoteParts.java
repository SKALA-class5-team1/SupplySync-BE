package com.skala.supplyquote.supplyquote_backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 견적을 이루는 공통 구조. 필드는 데이터셋 정답지 6_sg3_quote.json의 quote_draft와 같다. */
public final class QuoteParts {

    private QuoteParts() {
    }

    @Schema(description = "단가 구성 (모델 기본가, 옵션 추가가)")
    public record PriceBreakdown(
            @Schema(example = "OPT-HT") String code,
            @Schema(example = "2400") Integer amountKrw) {
    }

    @Schema(description = "부품 원가 구성")
    public record PartCost(
            @Schema(example = "P-MCU-G1") String partId,
            @Schema(example = "5300") Integer costKrw) {
    }

    @Schema(description = "담당자용 견적 상세. 부품 원가 내역을 포함한다.")
    public record ManagerDetails(
            String recipient,
            String incoterms,
            List<PriceBreakdown> priceBreakdown,
            List<PartCost> partsCostBreakdown) {
    }

    @Schema(description = "고객용 견적 상세. 원가 내역은 제외한다.")
    public record CustomerDetails(
            String recipient,
            String incoterms,
            List<PriceBreakdown> priceBreakdown) {
    }

    @Schema(description = "연도별 단가·마진 (내부용). 1년차가 기준가, 2년차부터 연 3% 인하 → 6년차는 5회 적용")
    public record PriceByYear(
            @Schema(example = "6") Integer year,
            @Schema(description = "누적 인하 적용 횟수", example = "5") Integer crApplied,
            @Schema(example = "32031") Integer unitPriceKrw,
            @Schema(description = "부품 원가 기준 마진(%). 고객에게 노출하지 않는다", example = "32.4") Double marginPct) {
    }

    @Schema(description = "고객에게 공개하는 연도별 단가 (마진 제외)")
    public record CustomerPriceByYear(
            @Schema(example = "6") Integer year,
            @Schema(example = "32031") Integer unitPriceKrw) {
    }

    /**
     * 견적 조건. type: ASSUMPTION(가정) | SUBSTITUTE_PART(대체 부품) | CONTRACT_PROPOSAL(계약 수정 제안).
     * 유형별로 field, partId, qty, extraCostKrw, ref가 선택적으로 채워진다.
     */
    @Schema(description = "견적 조건")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Condition(
            @Schema(example = "SUBSTITUTE_PART") String type,
            @Schema(example = "spec.flow") String field,
            @Schema(example = "시작품 2,000개 중 500개에 대체 제어칩(누리칩스) 적용") String text,
            @Schema(example = "P-MCU-G1-ALT") String partId,
            @Schema(example = "500") Integer qty,
            @Schema(example = "150000") Long extraCostKrw,
            @Schema(example = "제20조, 제21조") String ref) {
    }

    @Schema(description = "최종 검토 결과 (내부용). rule: PRICE | ANNUAL_CR | MARGIN | PRICE_RECALC | FORMAT")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Check(
            @Schema(example = "MARGIN") String rule,
            @Schema(example = "마진") String label,
            @Schema(example = "부품 원가 기준 30% 이상 (1년차·6년차)") String criterion,
            @Schema(example = "42.0% / 32.4%") String value,
            @Schema(description = "PASS | FAIL", example = "PASS") String result,
            @Schema(example = "제16조") String ref) {
    }
}
