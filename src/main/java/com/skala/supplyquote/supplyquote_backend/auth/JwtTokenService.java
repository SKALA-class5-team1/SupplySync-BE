package com.skala.supplyquote.supplyquote_backend.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final String issuer;
    private final Duration lifetime;

    public JwtTokenService(@Value("${app.jwt.secret-base64:}") String secretBase64,
                           @Value("${app.jwt.issuer:supplysync}") String issuer,
                           @Value("${app.jwt.access-token-seconds:3600}") long lifetimeSeconds) {
        byte[] secret;
        try {
            secret = Base64.getDecoder().decode(secretBase64);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("JWT_SECRET_BASE64는 Base64 인코딩한 32바이트 이상 키여야 합니다.", e);
        }
        if (secret.length < 32) throw new IllegalStateException("JWT_SECRET_BASE64는 32바이트 이상이어야 합니다.");
        if (lifetimeSeconds < 60 || lifetimeSeconds > 86400)
            throw new IllegalStateException("JWT_ACCESS_TOKEN_SECONDS는 60~86400초여야 합니다.");
        SecretKey key = new SecretKeySpec(secret, "HmacSHA256");
        this.encoder = NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        jwtDecoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        this.decoder = jwtDecoder;
        this.issuer = issuer;
        this.lifetime = Duration.ofSeconds(lifetimeSeconds);
    }

    public String issue(UserAccountService.Account user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject(Long.toString(user.userId()))
                .issuedAt(now).expiresAt(now.plus(lifetime))
                .claim("company_id", user.companyId()).claim("user_type", user.userType().name()).build();
        return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    public JwtDecoder decoder() { return decoder; }
    public int expiresInSeconds() { return Math.toIntExact(lifetime.getSeconds()); }
}
