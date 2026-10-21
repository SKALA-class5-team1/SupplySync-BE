package com.skala.supplyquote.supplyquote_backend.config;

import com.skala.supplyquote.supplyquote_backend.common.ApiException;
import com.skala.supplyquote.supplyquote_backend.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Agent·business MCP가 호출하는 내부 API에 공유 키를 요구한다. */
@Configuration
public class InternalApiKeyConfig implements WebMvcConfigurer {
    private final String expectedKey;

    public InternalApiKeyConfig(@Value("${app.internal-api-key:}") String expectedKey) {
        this.expectedKey = expectedKey;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                String actual = request.getHeader("X-Internal-Api-Key");
                if (expectedKey.isBlank() || actual == null || !MessageDigest.isEqual(
                        expectedKey.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8))) {
                    throw new ApiException(ErrorCode.UNAUTHORIZED, "내부 API 키가 없거나 올바르지 않습니다.");
                }
                return true;
            }
        }).addPathPatterns("/internal/**");
    }
}
