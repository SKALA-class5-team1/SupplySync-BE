package com.skala.supplyquote.supplyquote_backend.web.dto;

import com.skala.supplyquote.supplyquote_backend.domain.UserType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public final class AuthDto {

    private AuthDto() {
    }

    @Schema(description = "로그인 요청. 데모 계정: hb_buyer01(고객), ds_sales01(담당자), 비밀번호 demo1234")
    public record LoginRequest(
            @NotBlank @Schema(example = "ds_sales01") String loginId,
            @NotBlank @Schema(example = "demo1234") String password) {
    }

    @Schema(description = "로그인한 사용자 정보")
    public record UserInfo(
            @Schema(example = "2") Long userId,
            @Schema(example = "ds_sales01") String loginId,
            @Schema(example = "김영업") String name,
            @Schema(example = "MANAGER") UserType userType,
            @Schema(example = "2") Long companyId,
            @Schema(example = "대성오토텍") String companyName) {
    }

    @Schema(description = "로그인 응답. 이후 요청에 Authorization: Bearer {accessToken}을 붙인다.")
    public record LoginResponse(
            String accessToken,
            @Schema(example = "Bearer") String tokenType,
            @Schema(description = "만료까지 남은 초", example = "3600") Integer expiresIn,
            UserInfo user) {
    }
}
