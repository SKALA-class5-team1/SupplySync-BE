package com.skala.supplyquote.supplyquote_backend.domain;

/** 진행 이벤트(SSE) 종류. Agent가 보내고(POST /internal/tasks/{id}/events) Spring이 화면으로 중계한다. */
public enum EventType {
    STEP_START,
    STEP_END,
    TOOL_START,
    TOOL_END,
    TOKEN,
    /** HIGH 이슈로 멈춤. MANAGER에게만 중계한다 (이슈 내용 포함) */
    CLARIFICATION_REQUIRED,
    /** 견적 초안과 최종 검토가 끝나 승인 대기 */
    APPROVAL_REQUIRED,
    DONE,
    FAILED
}
