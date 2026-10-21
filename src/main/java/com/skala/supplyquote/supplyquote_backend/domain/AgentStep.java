package com.skala.supplyquote.supplyquote_backend.domain;

/**
 * Agent 워크플로 단계. Agent 진행 이벤트의 step 값이다.
 * ALTERNATIVE_ANALYSIS는 납기·재고를 충족하지 못할 때만 실행되는 조건부 단계이다.
 */
public enum AgentStep {
    /** SG1: 요구사항 추출, 누락·이상값 검증 */
    RFQ_ANALYSIS,
    /** SG1: 제품·옵션 선정, 계약 검토 */
    SPEC_MATCH,
    /** SG2: BOM 전개, 가용 재고, 조달 리드타임 */
    SUPPLY_CHECK,
    /** SG2 (조건부): 입고 대기, 분할 납품, 대체 부품, 신규 발주 */
    ALTERNATIVE_ANALYSIS,
    /** SG3: 원가·마진 계산, 견적 초안 */
    QUOTE,
    /** SG3: 규정·마진·수치·양식 검증 */
    FINAL_REVIEW,
    /** SG4: 담당자 승인 */
    APPROVAL,
    /** SG4: 최신 재고·가격 재검증 */
    REVALIDATION,
    /** SG4: 견적 등록 */
    REGISTRATION
}
