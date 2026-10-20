package com.skala.supplyquote.supplyquote_backend.domain;

/** 분석 결과(rfq_reviews) 상태. 데이터셋 CHECK 제약과 같다. WAITING은 담당자 입력 대기. */
public enum ReviewStatus {
    RUNNING,
    WAITING,
    DONE
}
