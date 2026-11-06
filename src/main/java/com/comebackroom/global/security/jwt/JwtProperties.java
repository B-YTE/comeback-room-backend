package com.comebackroom.global.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.util.Base64;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        Duration accessTokenExpiration
) {

    private static final int MINIMUM_SECRET_LENGTH_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("JWT secret 값은 필수입니다.");
        }

        secret = secret.trim();
        validateSecretLength(secret);

        if (accessTokenExpiration == null || accessTokenExpiration.isZero()
                || accessTokenExpiration.isNegative()) {
            throw new IllegalArgumentException("Access Token 만료시간은 0보다 커야 합니다.");
        }
    }

    SecretKey secretKey() {
        return new SecretKeySpec(decodeSecret(secret), "HmacSHA256");
    }

    private static void validateSecretLength(String secret) {
        if (decodeSecret(secret).length < MINIMUM_SECRET_LENGTH_BYTES) {
            throw new IllegalArgumentException("JWT secret은 Base64 디코딩 기준 32바이트 이상이어야 합니다.");
        }
    }

    private static byte[] decodeSecret(String secret) {
        try {
            return Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("JWT secret은 올바른 Base64 형식이어야 합니다.", e);
        }
    }
}
