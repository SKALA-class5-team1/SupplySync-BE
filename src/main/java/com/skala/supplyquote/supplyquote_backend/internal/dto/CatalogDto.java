package com.skala.supplyquote.supplyquote_backend.internal.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 카탈로그 응답. 필드명은 데이터셋 컬럼(models, options, parts, bom, inventory)을 그대로 따른다. */
public final class CatalogDto {

    private CatalogDto() {
    }

    @Schema(description = "옵션: 표준 모델에 붙여 사양과 BOM 부품을 바꾼다")
    public record OptionResponse(
            @Schema(example = "OPT-HT") String optionCode,
            @Schema(example = "고온 대응 (125℃)") String optionName,
            @Schema(description = "바뀌는 사양 항목(models 컬럼명)", example = "temp_max") String changesField,
            @Schema(example = "125") String newValue,
            @Schema(example = "0.00") BigDecimal addWeightKg,
            @Schema(example = "0") Integer addNoiseDb,
            @Schema(description = "옵션 추가 단가", example = "2400") Integer addPriceKrw,
            @Schema(description = "BOM에서 빠지는 부품", example = "P-MCU-STD") String replacesPartId,
            @Schema(description = "BOM에 들어가는 부품", example = "P-MCU-G1") String usesPartId) {
    }

    @Schema(description = "표준 모델 + 붙일 수 있는 옵션(model_options)")
    public record ModelResponse(
            @Schema(example = "EWP-12-B") String modelId,
            @Schema(example = "중형 워터펌프") String modelName,
            @Schema(example = "25.0") BigDecimal flowLpm,
            @Schema(example = "80") Integer headKpa,
            @Schema(example = "9.0") BigDecimal voltageMin,
            @Schema(example = "16.0") BigDecimal voltageMax,
            @Schema(example = "-40") Integer tempMin,
            @Schema(example = "110") Integer tempMax,
            @Schema(example = "IP67") String ipRating,
            @Schema(example = "42") Integer noiseDb,
            @Schema(example = "LIN,PWM") String comm,
            @JsonProperty("size_l_mm") @Schema(example = "110") Integer sizeLMm,
            @JsonProperty("size_w_mm") @Schema(example = "90") Integer sizeWMm,
            @JsonProperty("size_h_mm") @Schema(example = "90") Integer sizeHMm,
            @Schema(example = "0.70") BigDecimal weightKg,
            @Schema(description = "기본 판매 단가", example = "34000") Integer basePriceKrw,
            List<OptionResponse> options) {
    }

    /** 5_sg2_supply.json의 bom_effective 항목과 같다. */
    @Schema(description = "옵션 적용 BOM 한 줄")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BomLine(
            @Schema(example = "P-MCU-G1") String partId,
            @Schema(example = "제어칩 (고온용)") String partName,
            @Schema(example = "1") Integer qtyPerUnit,
            @Schema(description = "BASE 또는 OPTION:{옵션 코드}", example = "OPTION:OPT-HT") String source,
            @Schema(description = "옵션이 대신 넣은 원래 부품. BASE면 없음", example = "P-MCU-STD") String replacedPartId) {
    }

    @Schema(description = "옵션을 적용한 BOM. 옵션의 replaces 부품은 빠지고 uses 부품이 들어간다.")
    public record BomResponse(
            @Schema(example = "EWP-12-B") String modelId,
            @Schema(example = "[\"OPT-HT\", \"OPT-IP9K\"]") List<String> options,
            List<BomLine> items) {
    }

    @Schema(description = "부품 마스터")
    public record PartResponse(
            @Schema(example = "P-MCU-G1") String partId,
            @Schema(example = "제어칩 (고온용)") String partName,
            @Schema(example = "세온반도체") String supplier,
            @Schema(description = "개당 원가", example = "5300") Integer unitCostKrw,
            @Schema(description = "신규 발주 조달기간(일)", example = "120") Integer leadTimeDays,
            @Schema(example = "true") Boolean isCritical) {
    }

    @Schema(description = "부품 재고. available = onHand - allocated (기존 주문 배정분 제외). 입고 예정은 부품당 1건이다.")
    public record InventoryResponse(
            @Schema(example = "P-MCU-G1") String partId,
            @Schema(example = "9000") Integer onHand,
            @Schema(example = "7500") Integer allocated,
            @Schema(example = "1500") Integer available,
            @Schema(example = "5000") Integer incomingQty,
            @Schema(example = "2027-01-20") LocalDate incomingDate) {
    }

    @Schema(description = "대체 부품 후보 (part_substitutes + 대체 부품 정보)")
    public record SubstituteResponse(
            @Schema(description = "원래 부품", example = "P-MCU-G1") String partId,
            @Schema(example = "P-MCU-G1-ALT") String substitutePartId,
            @Schema(example = "제어칩 (고온용, 대체)") String substituteName,
            @Schema(example = "누리칩스") String substituteSupplier,
            @Schema(description = "적용 전 검증 시험 일수", example = "14") Integer validationDays,
            @Schema(description = "회로 변경 불필요 여부", example = "true") Boolean pinCompatible,
            @Schema(description = "근거 문서 청크", example = "alt-002") String refChunkId,
            @Schema(description = "개당 원가 차이(원) = 대체 부품 단가 - 원래 부품 단가", example = "300") Integer unitCostDiffKrw,
            @Schema(description = "대체 부품의 가용 재고", example = "3000") Integer availableQty) {
    }
}
