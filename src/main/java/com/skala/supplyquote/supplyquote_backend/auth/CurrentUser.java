package com.skala.supplyquote.supplyquote_backend.auth;

import com.skala.supplyquote.supplyquote_backend.common.ApiException;
import com.skala.supplyquote.supplyquote_backend.common.ErrorCode;
import com.skala.supplyquote.supplyquote_backend.domain.UserType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.Instant;

@Service
public class CurrentUser {
    private final UserAccountService users;
    private final JdbcTemplate jdbc;

    public CurrentUser(UserAccountService users, JdbcTemplate jdbc) {
        this.users = users;
        this.jdbc = jdbc;
    }

    public UserAccountService.Account get() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt))
            throw new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        long id;
        try { id = Long.parseLong(jwt.getSubject()); }
        catch (NumberFormatException e) { throw new ApiException(ErrorCode.UNAUTHORIZED, "잘못된 사용자 토큰입니다."); }
        return users.byId(id).filter(UserAccountService.Account::active)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "사용할 수 없는 계정입니다."));
    }

    public void requireRfq(Long rfqId) {
        var user = get();
        Integer visible = jdbc.queryForObject("""
                select count(*) from rfqs where rfq_id=? and (?='MANAGER' or customer_company_id=?)
                """, Integer.class, rfqId, user.userType().name(), user.companyId());
        if (visible == null || visible == 0) throw new ApiException(ErrorCode.NOT_FOUND, "없는 RFQ입니다: " + rfqId);
    }

    public void requireTask(java.util.UUID taskId) {
        var user = get();
        Integer visible = jdbc.queryForObject("""
                select count(*) from ai_tasks t join rfqs r on r.rfq_number=t.rfq_number
                where t.task_id=? and (?='MANAGER' or r.customer_company_id=?)
                """, Integer.class, taskId, user.userType().name(), user.companyId());
        if (visible == null || visible == 0) throw new ApiException(ErrorCode.NOT_FOUND, "없는 작업입니다: " + taskId);
    }

    public long requireManagerId() {
        var user = get();
        if (user.userType() != UserType.MANAGER)
            throw new ApiException(ErrorCode.FORBIDDEN, "담당자만 처리할 수 있습니다.");
        return user.userId();
    }

    public long tokenRemainingMillis() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt) || jwt.getExpiresAt() == null)
            throw new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        return Math.max(1L, Duration.between(Instant.now(), jwt.getExpiresAt()).toMillis());
    }
}
