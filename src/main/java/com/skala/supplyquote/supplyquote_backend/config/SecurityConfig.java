package com.skala.supplyquote.supplyquote_backend.config;

import com.skala.supplyquote.supplyquote_backend.auth.JwtTokenService;
import com.skala.supplyquote.supplyquote_backend.auth.UserAccountService;
import com.skala.supplyquote.supplyquote_backend.common.ErrorResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    JwtDecoder jwtDecoder(JwtTokenService tokens) { return tokens.decoder(); }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, UserAccountService users, JsonMapper json) throws Exception {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            long id;
            try { id = Long.parseLong(jwt.getSubject()); }
            catch (RuntimeException e) { throw new BadCredentialsException("잘못된 사용자 토큰입니다."); }
            var user = users.byId(id).filter(UserAccountService.Account::active)
                    .orElseThrow(() -> new BadCredentialsException("사용할 수 없는 계정입니다."));
            Object companyClaim = jwt.getClaim("company_id");
            if (!user.userType().name().equals(jwt.getClaimAsString("user_type"))
                    || !Long.toString(user.companyId()).equals(String.valueOf(companyClaim)))
                throw new BadCredentialsException("계정 정보가 변경되었습니다.");
            return List.of(new SimpleGrantedAuthority("ROLE_" + user.userType().name()));
        });
        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**",
                                "/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/internal/**").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/rfqs").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.GET, "/api/rfqs/*/analysis").hasRole("MANAGER")
                        .requestMatchers(HttpMethod.POST, "/api/ai/tasks/*/confirmation", "/api/ai/tasks/*/save-approval",
                                "/api/ai/tasks/*/approval")
                        .hasRole("MANAGER")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, error) -> writeError(response, json, 401,
                                "UNAUTHORIZED", "로그인이 필요하거나 토큰이 유효하지 않습니다."))
                        .accessDeniedHandler((request, response, error) -> writeError(response, json, 403,
                                "FORBIDDEN", "접근 권한이 없습니다.")));
        return http.build();
    }

    private static void writeError(jakarta.servlet.http.HttpServletResponse response, JsonMapper json,
                                   int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(json.writeValueAsString(ErrorResponse.of(code, message, List.of())));
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins:}") String origins) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Internal-Api-Key"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}
