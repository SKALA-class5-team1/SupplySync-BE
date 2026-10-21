package com.skala.supplyquote.supplyquote_backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 응답 상태가 400 이상인 요청을 한 줄로 남긴다. 보안 필터와 CORS 거부처럼 컨트롤러에 닿기 전에 막힌 요청도 보인다.
 * 요청 헤더(Authorization 등)와 본문은 남기지 않는다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestErrorLogFilter extends OncePerRequestFilter {
    /** 오류 응답을 만든 쪽이 사유를 남겨 두면 로그에 함께 찍는다. */
    public static final String ERROR_ATTRIBUTE = "supplysync.error";

    private static final Logger log = LoggerFactory.getLogger(RequestErrorLogFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException e) {
            log.error("처리되지 않은 예외 {} {} ({}ms)", request.getMethod(), request.getRequestURI(), elapsedMillis(start), e);
            throw e;
        }
        int status = response.getStatus();
        if (status < 400) return;
        String origin = request.getHeader("Origin");
        Object reason = request.getAttribute(ERROR_ATTRIBUTE);
        String line = "HTTP {} {} {} ({}ms) origin={} 사유={}";
        String hint = reason != null ? reason.toString()
                : status == 403 && origin != null
                ? "CORS 거부 가능성: 이 origin이 FRONTEND_ORIGINS에 없는지 확인하세요"
                : "-";
        if (status >= 500) {
            log.error(line, status, request.getMethod(), request.getRequestURI(), elapsedMillis(start), origin, hint);
        } else {
            log.warn(line, status, request.getMethod(), request.getRequestURI(), elapsedMillis(start), origin, hint);
        }
    }

    private static long elapsedMillis(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }
}
