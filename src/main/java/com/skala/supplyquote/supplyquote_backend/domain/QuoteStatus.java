package com.skala.supplyquote.supplyquote_backend.domain;

/** 견적 상태. 데이터셋 quotes.status CHECK 제약과 같다. 고객은 REGISTERED만 조회한다. */
public enum QuoteStatus {
    DRAFT,
    APPROVED,
    REJECTED,
    REGISTERED
}
