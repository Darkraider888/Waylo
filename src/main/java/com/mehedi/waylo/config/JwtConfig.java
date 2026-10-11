package com.mehedi.waylo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
@ConditionalOnProperty(name = "waylo.jwt.enabled", havingValue = "true")
public class JwtConfig {

    @Bean
    public SecretKey wayloJwtKey(@Value("${waylo.jwt.secret-base64:}") String secret) {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("WAYLO_JWT_SECRET_BASE64 must be valid Base64", e);
        }
        if (key.length < 32) {
            throw new IllegalStateException("JWT requires a random signing secret of at least 32 bytes.");
        }
        return new SecretKeySpec(key, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey wayloJwtKey) {
        return NimbusJwtEncoder.withSecretKey(wayloJwtKey)
                .algorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey wayloJwtKey, com.mehedi.waylo.repository.UserRepository users) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(wayloJwtKey)
                .macAlgorithm(MacAlgorithm.HS256).build();
        org.springframework.security.oauth2.core.OAuth2TokenValidator<Jwt> account = token -> {
            boolean enabled = token.getSubject() != null && users.findByEmail(token.getSubject())
                    .filter(com.mehedi.waylo.entity.User::isEnabled)
                    .filter(user -> token.getClaim("authVersion") instanceof Number version
                            && version.intValue() == user.getAuthVersion()
                            && user.getRole().name().equals(token.getClaimAsString("role"))).isPresent();
            return enabled ? org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success()
                    : org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.failure(
                        new org.springframework.security.oauth2.core.OAuth2Error("invalid_token", "Account unavailable", null));
        };
        decoder.setJwtValidator(new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer("waylo"), account));
        return decoder;
    }
}
