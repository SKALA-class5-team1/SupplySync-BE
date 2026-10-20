package com.skala.supplyquote.supplyquote_backend.auth;

import com.skala.supplyquote.supplyquote_backend.common.ErrorResponse;
import com.skala.supplyquote.supplyquote_backend.domain.UserType;
import com.skala.supplyquote.supplyquote_backend.web.dto.AuthDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증", description = "로그인과 현재 사용자 (화면용)")
@RestController
@RequestMapping("/api")
public class AuthController {

    @Operation(summary = "로그인",
            description = "[STUB] login_id와 비밀번호(BCrypt 검증)로 JWT를 발급한다. 비활성(is_active=false) 사용자는 거부한다. "
                    + "JWT claims: userId, companyId, userType. 역할: CUSTOMER(완성차 고객), MANAGER(대성오토텍 영업 담당).")
    @ApiResponse(responseCode = "200", description = "로그인 성공")
    @ApiResponse(responseCode = "401", description = "아이디 또는 비밀번호 불일치",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/auth/login")
    public AuthDto.LoginResponse login(@Valid @RequestBody AuthDto.LoginRequest request) {
        var user = new AuthDto.UserInfo(2L, request.loginId(), "김영업", UserType.MANAGER, 2L, "대성오토텍");
        return new AuthDto.LoginResponse("stub.jwt.token", "Bearer", 3600, user);
    }

    @Operation(summary = "현재 사용자", description = "[STUB] 토큰의 사용자 정보. 화면의 역할별 메뉴 구성에 쓴다.")
    @ApiResponse(responseCode = "401", description = "토큰 없음·만료",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/me")
    public AuthDto.UserInfo me() {
        return new AuthDto.UserInfo(2L, "ds_sales01", "김영업", UserType.MANAGER, 2L, "대성오토텍");
    }
}
