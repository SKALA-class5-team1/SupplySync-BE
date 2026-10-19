package com.skala.supplyquote.supplyquote_backend.domain;

/** 분석 결과(version 단위)의 상태. DB의 analysis_results.status CHECK 제약과 값을 맞춘다. */
public enum AnalysisResultStatus {
    DRAFT,
    WAITING_APPROVAL,
    APPROVED,
    REJECTED,
    /** 조건 변경으로 재계산되어 더 이상 유효하지 않은 이전 버전 */
    SUPERSEDED,
    REGISTERED
}
