package com.skala.supplyquote.supplyquote_backend.auth;

import com.skala.supplyquote.supplyquote_backend.common.ErrorResponse;
import com.skala.supplyquote.supplyquote_backend.common.ApiException;
import com.skala.supplyquote.supplyquote_backend.common.ErrorCode;
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
import org.springframework.security.crypto.password.PasswordEncoder;

@Tag(name = "인증", description = "로그인과 현재 사용자 (화면용)")
@RestController
@RequestMapping("/api")
public class AuthController {
    private final UserAccountService users;
    private final JwtTokenService tokens;
    private final CurrentUser current;
    private final PasswordEncoder passwords;

    public AuthController(UserAccountService users, JwtTokenService tokens, CurrentUser current, PasswordEncoder passwords) {
        this.users = users;
        this.tokens = tokens;
        this.current = current;
        this.passwords = passwords;
    }

    @Operation(summary = "로그인",
            description = "login_id와 비밀번호(BCrypt 검증)로 JWT를 발급한다. 비활성(is_active=false) 사용자는 거부한다. "
                    + "JWT claims: userId, companyId, userType. 역할: CUSTOMER(완성차 고객), MANAGER(대성오토텍 영업 담당).")
    @ApiResponse(responseCode = "200", description = "로그인 성공")
    @ApiResponse(responseCode = "401", description = "아이디 또는 비밀번호 불일치",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/auth/login")
    public AuthDto.LoginResponse login(@Valid @RequestBody AuthDto.LoginRequest request) {
        var user = users.byLoginId(request.loginId())
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."));
        if (!user.active() || !passwords.matches(request.password(), user.passwordHash()))
            throw new ApiException(ErrorCode.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다.");
        return new AuthDto.LoginResponse(tokens.issue(user), "Bearer", tokens.expiresInSeconds(), user.toUserInfo());
    }

    @Operation(summary = "현재 사용자", description = "토큰의 사용자 정보. 화면의 역할별 메뉴 구성에 쓴다.")
    @ApiResponse(responseCode = "401", description = "토큰 없음·만료",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/me")
    public AuthDto.UserInfo me() {
        return current.get().toUserInfo();
    }
}
