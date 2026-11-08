package com.comebackroom.global.security.refresh;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Locale;

@ConfigurationProperties(prefix = "app.refresh-token")
public record RefreshTokenProperties(
        Duration expiration,
        String cookieName,
        String cookiePath,
        boolean cookieSecure,
        String cookieSameSite
) {

    public RefreshTokenProperties {
        if (expiration == null || expiration.isZero() || expiration.isNegative()) {
            throw new IllegalArgumentException("Refresh Token 만료시간은 0보다 커야 합니다.");
        }

        cookieName = requireText(cookieName, "Refresh Token Cookie 이름은 필수입니다.");
        cookiePath = requireText(cookiePath, "Refresh Token Cookie 경로는 필수입니다.");
        if (!cookiePath.startsWith("/")) {
            throw new IllegalArgumentException("Refresh Token Cookie 경로는 /로 시작해야 합니다.");
        }

        cookieSameSite = normalizeSameSite(cookieSameSite);
        if ("None".equals(cookieSameSite) && !cookieSecure) {
            throw new IllegalArgumentException("SameSite=None은 Secure Cookie 설정이 필요합니다.");
        }
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String normalizeSameSite(String sameSite) {
        String normalizedSameSite = requireText(
                sameSite,
                "Refresh Token Cookie SameSite 값은 필수입니다."
        ).toLowerCase(Locale.ROOT);

        return switch (normalizedSameSite) {
            case "strict" -> "Strict";
            case "lax" -> "Lax";
            case "none" -> "None";
            default -> throw new IllegalArgumentException(
                    "Refresh Token Cookie SameSite 값은 Strict, Lax, None 중 하나여야 합니다."
            );
        };
    }
}
