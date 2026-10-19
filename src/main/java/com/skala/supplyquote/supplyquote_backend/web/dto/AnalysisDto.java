package com.skala.supplyquote.supplyquote_backend.web.dto;

import com.skala.supplyquote.supplyquote_backend.domain.AnalysisResultStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 분석 결과 모델. 화면 조회(응답)와 내부 저장(요청)이 같은 하위 구조를 쓴다.
 * 구조는 지영님 데이터셋의 정답 JSON(2_validation_issues.json, 3_spec_match.json, 4_output_state.json)을 따른다.
 * requirements는 1_parsed_rfq.json 그대로(자유 형식)이다.
 */
public final class AnalysisDto {

    private AnalysisDto() {
    }

    @Schema(description = "검증 이슈 (validate_rules 결과). 데이터셋의 ISS-01 ~ ISS-07")
    public record Issue(
            @Schema(example = "ISS-01") String id,
            @Schema(description = "이상값 | 누락 | 계약 리스크", example = "이상값") String type,
            @Schema(example = "spec.flow") String field,
            @Schema(description = "HIGH | MED | LOW. HIGH가 있으면 담당자 확인으로 멈춘다", example = "HIGH") String severity,
            @Schema(example = "22 L/h (= 0.37 L/min)") String found,
            @Schema(example = "정상 범위 5~60 L/min을 크게 벗어남. 단위 오기 의심") String reason,
            @Schema(example = "22 L/min으로 가정") String suggestion,
            @Schema(description = "근거 문서 (RAG 인용)", example = "design_standard.md §2") String ref) {
    }

    @Schema(description = "승인된 가정. 누락·이상값을 표준값으로 가정하고 담당자가 확인한 내역")
    public record Assumption(
            @Schema(example = "spec.flow") String field,
            @Schema(example = "22 L/min") String value,
            @Schema(example = "영업 담당") String approvedBy) {
    }

    @Schema(description = "계약 리스크 (check_contract 결과)")
    public record ContractRisk(
            @Schema(example = "품질 보증 5년/150,000km") String item,
            @Schema(example = "3년/100,000km") String limit,
            @Schema(example = "제12조") String ref) {
    }

    @Schema(description = "SG1 검토 결과 중 사람이 확인할 내용")
    public record Review(
            List<Issue> issues,
            List<Assumption> assumptions,
            @Schema(description = "고객에게 보낼 확인 질의서") List<String> customerQuestions,
            List<ContractRisk> contractRisks) {
    }

    @Schema(description = "표준 제품·옵션 대조 결과 (match_spec 결과)")
    public record ProductMatch(
            @Schema(example = "EWP-12-B") String selectedModel,
            @Schema(example = "[\"OPT-HT\", \"OPT-IP9K\"]") List<String> options,
            @Schema(description = "표준 | 파생 사양 | 신규 개발", example = "파생 사양") String fitType,
            @Schema(description = "모델 기본가 + 옵션 가격", example = "37300") Integer quotedPriceKrw,
            @Schema(example = "38000") Integer targetPriceKrw) {
    }

    @Schema(description = "부족 부품: 무엇이, 언제, 얼마나 부족한가")
    public record Shortage(
            @Schema(example = "P-MCU-G1") String partId,
            @Schema(example = "제어칩 (고온용)") String partName,
            @Schema(description = "필요 수량 (시작품 수량 × 단위당 수량)", example = "2000") Integer requiredQty,
            @Schema(description = "기존 주문 배정분을 제외한 가용 재고", example = "1500") Integer availableQty,
            @Schema(example = "500") Integer shortageQty,
            @Schema(example = "5000") Integer incomingQty,
            @Schema(description = "입고 예정일. 없으면 null", example = "2027-01-20") LocalDate incomingDate,
            @Schema(description = "고객 납기", example = "2026-12-15") LocalDate dueDate,
            @Schema(description = "입고 예정일이 납기보다 늦은가", example = "true") Boolean incomingAfterDue) {
    }

    @Schema(description = "대안: 기본안, 대체 부품, 분할 납품")
    public record Alternative(
            @Schema(example = "ALT-2") String alternativeId,
            @Schema(description = "BASE | SUBSTITUTE_PART | PARTIAL_DELIVERY", example = "SUBSTITUTE_PART") String type,
            @Schema(example = "부족 500개를 P-MCU-G1-ALT로 대체") String description,
            @Schema(description = "기본안 대비 추가 원가(원)", example = "150000") Long additionalCostKrw,
            @Schema(description = "납품 가능 시점", example = "2026-12-15") LocalDate readyDate,
            @Schema(description = "고객 납기를 맞추는가", example = "true") Boolean meetsDueDate,
            @Schema(description = "적용 전 검증 시험 일수", example = "14") Integer validationDays,
            @Schema(description = "사양 재검증까지 통과한 실현 가능한 안인지") Boolean feasible,
            @Schema(description = "Agent가 추천하는 안") Boolean recommended,
            @Schema(description = "대안별 상세(대체 부품, 수량, 단가 차이 등)") Map<String, Object> details) {
    }

    @Schema(description = "분석 결과 조회 응답")
    public record Response(
            Long rfqId,
            UUID taskId,
            @Schema(description = "반려 후 재분석하면 증가한다. 승인 요청에 그대로 되돌려 보낸다", example = "1") Integer version,
            @Schema(example = "WAITING_APPROVAL") AnalysisResultStatus status,
            @Schema(description = "RFQ에서 추출한 구조화 요구사항 (1_parsed_rfq.json 구조)") Map<String, Object> requirements,
            ProductMatch productMatch,
            List<Shortage> shortages,
            List<Alternative> alternatives,
            Review review,
            @Schema(example = "ALT-2") String selectedAlternativeId,
            @Schema(description = "사람이 검토할 제안서 본문(근거 포함)") String proposal,
            @Schema(description = "견적 합계(원) = 단가 × 시작품 수량 + 추가 원가", example = "74750000") BigDecimal totalPrice,
            @Schema(description = "제안 납품일", example = "2026-12-15") LocalDate deliveryDate,
            Instant createdAt) {
    }

    @Schema(description = "분석 결과 저장 요청 (Agent → /internal/analysis-results)")
    public record SaveRequest(
            @NotNull UUID taskId,
            @NotNull @Schema(description = "저장 멱등 키. Agent가 생성한다") UUID requestId,
            Map<String, Object> requirements,
            ProductMatch productMatch,
            List<Shortage> shortages,
            List<Alternative> alternatives,
            Review review,
            String selectedAlternativeId,
            @NotBlank String proposal,
            @NotNull @Positive BigDecimal totalPrice,
            @NotNull LocalDate deliveryDate) {
    }

    @Schema(description = "분석 결과 저장 응답")
    public record SaveResponse(
            Long analysisResultId,
            UUID taskId,
            @Schema(description = "서버가 부여한 버전", example = "1") Integer version,
            @Schema(example = "WAITING_APPROVAL") AnalysisResultStatus status) {
    }
}
