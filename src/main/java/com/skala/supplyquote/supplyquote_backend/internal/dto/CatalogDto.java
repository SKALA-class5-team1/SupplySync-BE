package com.skala.supplyquote.supplyquote_backend.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/** 카탈로그 응답. 필드명은 데이터셋 컬럼(models, options, parts, bom)을 camelCase로 옮긴 것이다. */
public final class CatalogDto {

    private CatalogDto() {
    }

    @Schema(description = "옵션: 표준 모델에 붙여 사양과 BOM 부품을 바꾼다")
    public record OptionResponse(
            @Schema(example = "OPT-HT") String optionCode,
            @Schema(example = "고온 대응 (125℃)") String optionName,
            @Schema(description = "바뀌는 사양 항목(models 컬럼명)", example = "temp_max") String changesField,
            @Schema(description = "바뀐 값", example = "125") String newValue,
            @Schema(example = "0.00") BigDecimal addWeightKg,
            @Schema(example = "0") Integer addNoiseDb,
            @Schema(example = "2400") Integer addPriceKrw,
            @Schema(description = "BOM에서 빠지는 부품", example = "P-MCU-STD") String replacesPartId,
            @Schema(description = "BOM에 들어가는 부품", example = "P-MCU-G1") String usesPartId) {
    }

    @Schema(description = "표준 모델")
    public record ModelResponse(
            @Schema(example = "EWP-12-B") String modelId,
            @Schema(example = "중형 워터펌프") String modelName,
            @Schema(description = "유량 (L/min)", example = "25.0") BigDecimal flowLpm,
            @Schema(description = "위 유량을 내는 수두 기준 (kPa)", example = "80") Integer headKpa,
            @Schema(example = "9.0") BigDecimal voltageMin,
            @Schema(example = "16.0") BigDecimal voltageMax,
            @Schema(example = "-40") Integer tempMin,
            @Schema(example = "110") Integer tempMax,
            @Schema(description = "IP67 < IP6K9K", example = "IP67") String ipRating,
            @Schema(description = "소음 dB(A)", example = "42") Integer noiseDb,
            @Schema(description = "지원 통신", example = "LIN,PWM") String comm,
            @Schema(example = "110") Integer sizeLMm,
            @Schema(example = "90") Integer sizeWMm,
            @Schema(example = "90") Integer sizeHMm,
            @Schema(example = "0.70") BigDecimal weightKg,
            @Schema(example = "34000") Integer basePriceKrw,
            @Schema(description = "이 모델에 붙일 수 있는 옵션 (model_options)") List<OptionResponse> options) {
    }

    @Schema(description = "부품 마스터")
    public record PartResponse(
            @Schema(example = "P-MCU-G1") String partId,
            @Schema(example = "제어칩 (고온용)") String partName,
            @Schema(example = "세온반도체") String supplier,
            @Schema(example = "5300") Integer unitCostKrw,
            @Schema(description = "신규 발주 시 입고까지 걸리는 일수", example = "120") Integer leadTimeDays,
            @Schema(description = "부족하면 생산 불가한 핵심 부품", example = "true") Boolean critical) {
    }

    @Schema(description = "BOM 한 줄 (옵션 적용 후)")
    public record BomLine(
            @Schema(example = "P-MCU-G1") String partId,
            @Schema(example = "제어칩 (고온용)") String partName,
            @Schema(example = "세온반도체") String supplier,
            @Schema(description = "제품 1개당 필요 수량", example = "1") Integer qtyPerUnit,
            @Schema(example = "5300") Integer unitCostKrw,
            @Schema(example = "120") Integer leadTimeDays,
            @Schema(example = "true") Boolean critical,
            @Schema(description = "기본 구성이면 BASE, 옵션이 넣은 부품이면 그 옵션 코드", example = "OPT-HT") String source) {
    }

    @Schema(description = "옵션을 적용한 BOM. 옵션의 replaces 부품은 빠지고 uses 부품이 들어간다.")
    public record BomResponse(
            @Schema(example = "EWP-12-B") String modelId,
            @Schema(example = "[\"OPT-HT\", \"OPT-IP9K\"]") List<String> options,
            List<BomLine> items,
            @Schema(description = "옵션 때문에 BOM에서 빠진 부품", example = "[\"P-MCU-STD\", \"P-SEAL-STD\"]") List<String> replacedPartIds) {
    }

    @Schema(description = "대체 부품 후보")
    public record SubstituteResponse(
            @Schema(description = "원래 부품", example = "P-MCU-G1") String partId,
            @Schema(example = "P-MCU-G1-ALT") String substitutePartId,
            @Schema(example = "제어칩 (고온용, 대체)") String substituteName,
            @Schema(example = "누리칩스") String supplier,
            @Schema(example = "같은 고온 등급, 핀 배치 동일") String compatibilityNote,
            @Schema(description = "적용 전 검증 시험 일수", example = "14") Integer validationDays,
            @Schema(description = "원래 부품 대비 개당 원가 차이(원). 대체 부품 단가 − 원래 부품 단가", example = "300") Integer unitCostDeltaKrw,
            @Schema(description = "대체 부품의 가용 재고", example = "3000") Integer availableQty,
            @Schema(description = "선택하면 사양 재검증이 필요한지", example = "true") Boolean requiresSpecRevalidation) {
    }
}
