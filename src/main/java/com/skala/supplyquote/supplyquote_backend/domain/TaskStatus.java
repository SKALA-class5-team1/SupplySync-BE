package com.skala.supplyquote.supplyquote_backend.domain;

/** AI 분석 작업의 상태. DB의 ai_tasks.status CHECK 제약과 값을 맞춘다. */
public enum TaskStatus {
    QUEUED,
    RUNNING,
    /** HIGH 이슈(예: 유량 단위 오기)로 담당자의 가정 확인을 기다린다 */
    NEEDS_CLARIFICATION,
    /** SG3가 견적을 저장하기 전에 담당자의 저장 승인을 기다린다 */
    WAITING_SAVE_APPROVAL,
    /** 견적 초안과 최종 검토가 끝나 담당자 승인을 기다린다 */
    WAITING_APPROVAL,
    /** 승인 후 최신 재고·가격으로 다시 검증하는 중 */
    REVALIDATING,
    REGISTERING,
    /** 가능한 대안이 없어 담당자 검토 대기 */
    ON_HOLD,
    DONE,
    FAILED
}
