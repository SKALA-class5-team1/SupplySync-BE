package com.skala.supplyquote.supplyquote_backend.web;

import com.skala.supplyquote.supplyquote_backend.domain.AnalysisResultStatus;
import com.skala.supplyquote.supplyquote_backend.web.dto.AnalysisDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 스텁 응답용 샘플. 지영님 데이터셋(RFQ HB-EV7-EWP-001)의 정답 JSON 값을 그대로 옮겼다.
 * 실제 구현이 들어오면 이 클래스는 삭제한다.
 */
final class StubData {

    static final UUID TASK_ID = UUID.fromString("3f2b8c1e-5a4d-4e7b-9c1a-2d6f8e0b7a11");
    static final String RFQ_NUMBER = "HB-EV7-EWP-001";
    static final String RFQ_TITLE = "HB-EV7 전동식 워터펌프 견적 요청";
    static final Instant CREATED_AT = Instant.parse("2026-10-19T01:00:00Z");

    private StubData() {
    }

    static AnalysisDto.Response analysis(Long rfqId) {
        return new AnalysisDto.Response(
                rfqId, TASK_ID, 1, AnalysisResultStatus.WAITING_APPROVAL,
                requirements(),
                new AnalysisDto.ProductMatch("EWP-12-B", List.of("OPT-HT", "OPT-IP9K"), "파생 사양", 37300, 38000),
                List.of(new AnalysisDto.Shortage("P-MCU-G1", "제어칩 (고온용)", 2000, 1500, 500, 5000,
                        LocalDate.parse("2027-01-20"), LocalDate.parse("2026-12-15"), true)),
                alternatives(),
                review(),
                "ALT-2",
                "표준 모델 EWP-12-B에 고온(OPT-HT)·방수(OPT-IP9K) 옵션을 적용하면 요구 사양을 충족합니다(37,300원, 목표 38,000원 이내). "
                        + "다만 고온용 제어칩 P-MCU-G1은 가용 재고 1,500개로 시작품 2,000개에 500개 부족하고 입고 예정(2027-01-20)도 납기(2026-12-15)보다 늦습니다. "
                        + "부족분 500개를 대체 칩 P-MCU-G1-ALT(개당 +300원, 검증 2주)로 공급하는 안을 추천합니다.",
                new BigDecimal("74750000"), LocalDate.parse("2026-12-15"), Instant.parse("2026-10-19T01:03:00Z"));
    }

    private static Map<String, Object> requirements() {
        return Map.of(
                "rfq_id", RFQ_NUMBER,
                "customer", "한별모터스",
                "item", "전동식 워터펌프 (EWP, 12V)",
                "volume", Map.of("eau", 120000, "supply_years", 6, "lifetime_volume", 900000,
                        "sample_qty", 2000, "sample_due_date", "2026-12-15"),
                "schedule", Map.of("sop_date", "2027-09-01", "ppap_date", "2027-10-15"),
                "spec", Map.of("flow", Map.of("value", 22, "unit", "L/h"), "head_kpa", 70,
                        "temp_max", 120, "ip_rating", "IP6K9K", "noise_db_max", 45, "comm", "LIN"));
    }

    private static List<AnalysisDto.Alternative> alternatives() {
        var base = new AnalysisDto.Alternative("ALT-1", "BASE",
                "P-MCU-G1 입고(2027-01-20)를 기다린다. 시작품 납기보다 늦다.",
                0L, LocalDate.parse("2027-01-20"), false, 0, false, false, Map.of());
        var substitute = new AnalysisDto.Alternative("ALT-2", "SUBSTITUTE_PART",
                "부족 500개를 대체 칩 P-MCU-G1-ALT(가용 3,000개)로 공급한다. 개당 +300원, 적용 전 검증 시험 2주.",
                150000L, LocalDate.parse("2026-12-15"), true, 14, true, true,
                Map.of("partId", "P-MCU-G1", "substitutePartId", "P-MCU-G1-ALT", "quantity", 500,
                        "unitCostDeltaKrw", 300, "requiresSpecRevalidation", true));
        var partial = new AnalysisDto.Alternative("ALT-3", "PARTIAL_DELIVERY",
                "가용 1,500개를 납기에 먼저 납품하고 나머지 500개는 입고 후(2027-01-20) 납품한다. 고객 합의가 필요하다.",
                0L, LocalDate.parse("2026-12-15"), false, 0, true, false,
                Map.of("firstDeliveryQty", 1500, "secondDeliveryQty", 500, "secondDeliveryDate", "2027-01-20"));
        return List.of(base, substitute, partial);
    }

    private static AnalysisDto.Review review() {
        var issues = List.of(
                new AnalysisDto.Issue("ISS-01", "이상값", "spec.flow", "HIGH", "22 L/h (= 0.37 L/min)",
                        "정상 범위 5~60 L/min을 크게 벗어남. 단위 오기 의심", "22 L/min으로 가정", "design_standard.md §2"),
                new AnalysisDto.Issue("ISS-02", "누락", "spec.connector", "MED", "TBD", null,
                        "표준 4핀 커넥터로 가정, 고객 확인", "design_standard.md §7"),
                new AnalysisDto.Issue("ISS-03", "누락", "spec.coolant", "MED", "미기재", null,
                        "에틸렌글리콜 50% 혼합액으로 가정, 고객 확인", "design_standard.md §7"),
                new AnalysisDto.Issue("ISS-04", "이상값", "volume.lifetime_volume", "MED", "900,000",
                        "120,000 × 6년 = 720,000과 불일치", "고객 확인 (공급 기간 7.5년인지, 총 물량 오기인지)", null),
                new AnalysisDto.Issue("ISS-05", "이상값", "schedule.ppap_date", "MED", "PPAP 2027-10-15 > SOP 2027-09-01",
                        "양산 승인이 양산 시작보다 늦음", "고객 확인", null),
                new AnalysisDto.Issue("ISS-06", "계약 리스크", "contract.warranty", "MED", "5년 / 150,000 km",
                        "사내 한도 3년 / 100,000 km 초과", "한도 내 조정 협의 또는 품질본부장 승인 + 단가 반영", "sales_policy.md 제12조"),
                new AnalysisDto.Issue("ISS-07", "계약 리스크", "contract.penalty_cap_pct", "MED", "상한 없음",
                        "사내 상한 계약금액 10%", "배상 상한 10% 설정 협의", "sales_policy.md 제13조"));
        var assumptions = List.of(
                new AnalysisDto.Assumption("spec.flow", "22 L/min", "영업 담당"),
                new AnalysisDto.Assumption("spec.connector", "표준 4핀", "영업 담당"),
                new AnalysisDto.Assumption("spec.coolant", "에틸렌글리콜 50%", "영업 담당"));
        var questions = List.of(
                "유량 단위가 L/min이 맞는지 확인 부탁드립니다. (사양서 표기: 22 L/h)",
                "커넥터 사양(핀 수, 형상)을 알려주십시오.",
                "사용 냉각수 종류를 알려주십시오.",
                "총 공급 물량 900,000개와 연간 120,000개 × 6년(720,000개)이 다릅니다. 어느 쪽이 맞는지 확인 부탁드립니다.",
                "PPAP 완료일(2027-10-15)이 양산 시작일(2027-09-01)보다 늦습니다. 일정 확인 부탁드립니다.");
        var risks = List.of(
                new AnalysisDto.ContractRisk("품질 보증 5년/150,000km", "3년/100,000km", "제12조"),
                new AnalysisDto.ContractRisk("지연 배상 상한 없음", "계약금액 10%", "제13조"));
        return new AnalysisDto.Review(issues, assumptions, questions, risks);
    }
}
