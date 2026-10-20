package com.skala.supplyquote.supplyquote_backend.domain;

/** RFQ 상태. 데이터셋 rfqs.status CHECK 제약과 같다. */
public enum RfqStatus {
    RECEIVED,
    IN_REVIEW,
    QUOTED
}
