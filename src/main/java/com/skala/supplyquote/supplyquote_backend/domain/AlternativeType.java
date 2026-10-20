package com.skala.supplyquote.supplyquote_backend.domain;

/** 부품 부족 시 대안 유형 (SG2). rfq_reviews.recommended_alt도 이 값이다. */
public enum AlternativeType {
    /** 입고 대기: 예정 입고를 기다린다 */
    WAIT_INCOMING,
    /** 분할 납품: 가용분은 납기 내, 나머지는 입고 후 */
    PARTIAL_DELIVERY,
    /** 대체 부품 (타 공급처): 예) P-MCU-G1 → 누리칩스 P-MCU-G1-ALT */
    SUBSTITUTE_PART,
    /** 원 공급사 신규 발주 */
    NEW_ORDER
}
