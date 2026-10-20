package com.skala.supplyquote.supplyquote_backend.common;

import java.util.List;

/** 업무 규칙 위반을 HTTP 오류로 바꾸는 예외. GlobalExceptionHandler가 ErrorResponse로 변환한다. */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final List<String> details;

    public ApiException(ErrorCode code, String message) {
        this(code, message, List.of());
    }

    public ApiException(ErrorCode code, String message, List<String> details) {
        super(message);
        this.code = code;
        this.details = details;
    }

    public ErrorCode code() {
        return code;
    }

    public List<String> details() {
        return details;
    }
}
