package com.skala.supplyquote.supplyquote_backend.auth;

import com.skala.supplyquote.supplyquote_backend.domain.UserType;
import com.skala.supplyquote.supplyquote_backend.web.dto.AuthDto;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class UserAccountService {
    private final JdbcTemplate jdbc;

    public UserAccountService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Account> byLoginId(String loginId) {
        return jdbc.query("""
                select u.user_id,u.login_id,u.password_hash,u.name,u.user_type,u.company_id,u.is_active,
                       c.name as company_name from users u join companies c on c.company_id=u.company_id
                where u.login_id=?
                """, (rs, row) -> new Account(rs.getLong("user_id"), rs.getString("login_id"),
                rs.getString("password_hash"), rs.getString("name"), UserType.valueOf(rs.getString("user_type")),
                rs.getLong("company_id"), rs.getString("company_name"), rs.getBoolean("is_active")), loginId)
                .stream().findFirst();
    }

    public Optional<Account> byId(long id) {
        return jdbc.query("""
                select u.user_id,u.login_id,u.password_hash,u.name,u.user_type,u.company_id,u.is_active,
                       c.name as company_name from users u join companies c on c.company_id=u.company_id
                where u.user_id=?
                """, (rs, row) -> new Account(rs.getLong("user_id"), rs.getString("login_id"),
                rs.getString("password_hash"), rs.getString("name"), UserType.valueOf(rs.getString("user_type")),
                rs.getLong("company_id"), rs.getString("company_name"), rs.getBoolean("is_active")), id)
                .stream().findFirst();
    }

    public record Account(long userId, String loginId, String passwordHash, String name,
                          UserType userType, long companyId, String companyName, boolean active) {
        public AuthDto.UserInfo toUserInfo() {
            return new AuthDto.UserInfo(userId, loginId, name, userType, companyId, companyName);
        }
    }
}
