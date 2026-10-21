package com.skala.supplyquote.supplyquote_backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.skala.supplyquote.supplyquote_backend.domain.AlternativeType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * 분석 결과(rfq_reviews)를 이루는 공통 구조. 화면 조회와 Agent 저장이 같은 구조를 쓴다.
 * 필드는 데이터셋 정답지(05_expected 2·4·5번 파일)와 같다.
 */
public final class ReviewParts {

    private ReviewParts() {
    }

    @Schema(description = "RAG 근거. 이슈·계약 리스크·대안 항목에 함께 저장한다")
    public record Evidence(
            @Schema(description = "문서 청크 ID", example = "dsn-002") String chunkId,
            @Schema(description = "문서 내 위치", example = "2.1") String section,
            @Schema(description = "인용 내용") String excerpt) {
    }

    @Schema(description = "검증 이슈 (validate_rules 결과). 2_validation_issues.json의 issues")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Issue(
            @Schema(example = "ISS-01") String id,
            @Schema(description = "이상값 | 누락 | 계약 리스크", example = "이상값") String type,
            @Schema(example = "spec.flow") String field,
            @Schema(description = "HIGH | MED | LOW. HIGH가 있으면 담당자 확인으로 멈춘다", example = "HIGH") String severity,
            @Schema(example = "22 L/h (= 0.37 L/min)") String found,
            @Schema(example = "정상 범위 5~60 L/min을 크게 벗어남. 단위 오기 의심") String reason,
            @Schema(example = "22 L/min으로 가정") String suggestion,
            @Schema(description = "근거 문서 (RAG 인용)", example = "design_standard.md §2") String ref,
            List<Evidence> evidence) {
    }

    @Schema(description = "승인된 가정. 4_output_state.json의 assumptions")
    public record Assumption(
            @Schema(example = "spec.flow") String field,
            @Schema(example = "22 L/min") String value,
            @Schema(example = "영업 담당") String approvedBy) {
    }

    @Schema(description = "계약 리스크 (check_contract 결과). 4_output_state.json의 contract_risks")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ContractRisk(
            @Schema(example = "품질 보증 5년/150,000km") String item,
            @Schema(example = "3년/100,000km") String limit,
            @Schema(example = "제12조") String ref,
            List<Evidence> evidence) {
    }

    @Schema(description = "부족 부품 (SG2). 5_sg2_supply.json의 shortages")
    public record Shortage(
            @Schema(example = "P-MCU-G1") String partId,
            @Schema(example = "제어칩 (고온용)") String partName,
            @Schema(example = "2000") Integer requiredQty,
            @Schema(description = "가용 재고 = on_hand - allocated", example = "1500") Integer available,
            @Schema(example = "500") Integer shortQty,
            @Schema(example = "5000") Integer incomingQty,
            @Schema(example = "2027-01-20") LocalDate incomingDate,
            @Schema(description = "입고 예정일이 고객 납기보다 늦은 일수", example = "36") Integer incomingDelayDays,
            @Schema(description = "신규 발주 조달기간(일)", example = "120") Integer leadTimeDays,
            @Schema(example = "true") Boolean isCritical) {
    }

    /**
     * 대안. 4종 모두 공통 필드를 쓰고, 유형별로 아래 선택 필드가 추가된다.
     * PARTIAL_DELIVERY: onTimeQty, lateQty / SUBSTITUTE_PART: substitutePartId, substituteSupplier,
     * validationDays, unitCostDiffKrw / NEW_ORDER: leadTimeDays
     */
    @Schema(description = "대안 (SG2). 5_sg2_supply.json의 alternatives")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Alternative(
            AlternativeType type,
            @Schema(example = "대체 부품 (타 공급처)") String label,
            @Schema(example = "P-MCU-G1") String targetPartId,
            @Schema(description = "이 대안으로 채우는 수량", example = "500") Integer coveredQty,
            @Schema(description = "사용 가능일", example = "2026-11-02") LocalDate availableDate,
            @Schema(description = "고객 납기를 맞추는가", example = "true") Boolean meetsDueDate,
            @Schema(description = "납기 초과 일수", example = "0") Integer delayDays,
            @Schema(description = "기본안 대비 추가 비용(원)", example = "150000") Long extraCostKrw,
            @Schema(description = "실행 가능 여부", example = "true") Boolean feasible,
            @Schema(description = "Agent 추천 여부", example = "true") Boolean recommended,
            String reason,
            @Schema(description = "근거 청크 ID", example = "[\"alt-002\", \"pol-009\"]") List<String> refs,
            @Schema(description = "PARTIAL_DELIVERY: 납기 내 납품 수량") Integer onTimeQty,
            @Schema(description = "PARTIAL_DELIVERY: 납기 후 납품 수량") Integer lateQty,
            @Schema(description = "SUBSTITUTE_PART: 대체 부품", example = "P-MCU-G1-ALT") String substitutePartId,
            @Schema(description = "SUBSTITUTE_PART: 대체 공급사", example = "누리칩스") String substituteSupplier,
            @Schema(description = "SUBSTITUTE_PART: 적용 전 검증 일수", example = "14") Integer validationDays,
            @Schema(description = "SUBSTITUTE_PART: 개당 원가 차이(원)", example = "300") Integer unitCostDiffKrw,
            @Schema(description = "NEW_ORDER: 신규 발주 조달기간(일)", example = "120") Integer leadTimeDays,
            List<Evidence> evidence) {
    }
}
