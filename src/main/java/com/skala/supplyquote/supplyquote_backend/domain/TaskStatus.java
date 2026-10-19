package com.skala.supplyquote.supplyquote_backend.domain;

/** AI 분석 작업의 상태. DB의 ai_tasks.status CHECK 제약과 값을 맞춘다. */
public enum TaskStatus {
    QUEUED,
    RUNNING,
    /** 요구사항 누락·모순: 영업 담당자 확인 후 재개 */
    NEEDS_CLARIFICATION,
    WAITING_APPROVAL,
    /** 승인 후 최신 재고·가격·납기로 다시 검증하는 중 */
    REVALIDATING,
    REGISTERING,
    /** 견적은 등록됐고 구매요청만 실패한 경우처럼 일부만 성공 */
    PARTIALLY_REGISTERED,
    /** 가능한 대안이 없어 담당자 검토 대기 */
    ON_HOLD,
    DONE,
    FAILED
}
