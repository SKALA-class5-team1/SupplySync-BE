package com.skala.supplyquote.supplyquote_backend.common;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * 모든 API의 공통 오류 응답.
 * code 예: VALIDATION_ERROR, NOT_FOUND, STALE_VERSION, UNAUTHORIZED, FORBIDDEN
 */
@Schema(description = "공통 오류 응답")
public record ErrorResponse(
        @Schema(example = "VALIDATION_ERROR") String code,
        @Schema(example = "요청 값이 올바르지 않습니다.") String message,
        @Schema(description = "필드별 상세 사유", example = "[\"quantity: 0보다 커야 합니다\"]") List<String> details,
        Instant timestamp) {

    public static ErrorResponse of(String code, String message, List<String> details) {
        return new ErrorResponse(code, message, details, Instant.now());
    }
}
