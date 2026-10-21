package com.skala.supplyquote.supplyquote_backend.common;

import com.skala.supplyquote.supplyquote_backend.config.RequestErrorLogFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException e, HttpServletRequest request) {
        remember(request, e.code().name(), e.getMessage());
        if (e.code().status().is5xxServerError()) {
            log.error("서버 오류 {} {}: {}", request.getMethod(), request.getRequestURI(), e.getMessage(), e);
        }
        return ResponseEntity.status(e.code().status())
                .body(ErrorResponse.of(e.code().name(), e.getMessage(), e.details()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
        List<String> details = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .toList();
        remember(request, ErrorCode.VALIDATION_ERROR.name(), String.join(", ", details));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCode.VALIDATION_ERROR.name(), "요청 값이 올바르지 않습니다.", details));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(MissingServletRequestPartException e, HttpServletRequest request) {
        remember(request, ErrorCode.VALIDATION_ERROR.name(), "필수 항목 없음: " + e.getRequestPartName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCode.VALIDATION_ERROR.name(), "필수 항목이 없습니다.", List.of(e.getRequestPartName())));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e, HttpServletRequest request) {
        remember(request, ErrorCode.MALFORMED_REQUEST.name(), "본문 파싱 실패: " + e.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCode.MALFORMED_REQUEST.name(), "요청 본문을 읽을 수 없습니다.", List.of()));
    }

    /**
     * 위에서 처리하지 못한 예외는 원인을 로그로 남기고 공통 오류 형식의 500으로 응답한다.
     * 프레임워크(404, 405, 415 등)와 시큐리티가 이미 상태를 정한 예외는 건드리지 않고 그대로 넘긴다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e, HttpServletRequest request) throws Exception {
        if (e instanceof org.springframework.web.ErrorResponse
                || e instanceof AccessDeniedException || e instanceof AuthenticationException) {
            throw e;
        }
        remember(request, ErrorCode.INTERNAL_ERROR.name(), e.getClass().getSimpleName() + ": " + e.getMessage());
        log.error("예상하지 못한 오류 {} {}", request.getMethod(), request.getRequestURI(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR.name(), "서버 오류가 발생했습니다.", List.of()));
    }

    private static void remember(HttpServletRequest request, String code, String message) {
        request.setAttribute(RequestErrorLogFilter.ERROR_ATTRIBUTE, code + ": " + message);
    }
}
