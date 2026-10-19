package com.skala.supplyquote.supplyquote_backend.domain;

/** RFQ 상태. DB의 rfqs.status CHECK 제약과 값을 맞춘다. */
public enum RfqStatus {
    SUBMITTED,
    ANALYZING,
    WAITING_APPROVAL,
    COMPLETED,
    ON_HOLD,
    FAILED
}
