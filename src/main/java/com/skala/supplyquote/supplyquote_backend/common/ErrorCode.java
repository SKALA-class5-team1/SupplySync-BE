package com.skala.supplyquote.supplyquote_backend.common;

import org.springframework.http.HttpStatus;

/** API 오류 코드. ErrorResponse.code로 내려간다. */
public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    /** 없는 리소스. 다른 회사의 리소스도 존재 여부를 알리지 않기 위해 이 코드로 응답한다 */
    NOT_FOUND(HttpStatus.NOT_FOUND),
    /** 상태 불일치, 이미 처리됨 (승인 더블 클릭, 오래된 quote_id 등) */
    CONFLICT_STATE(HttpStatus.CONFLICT),
    AGENT_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
