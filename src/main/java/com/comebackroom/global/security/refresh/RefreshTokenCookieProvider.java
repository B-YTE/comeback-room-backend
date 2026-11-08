package com.comebackroom.global.security.refresh;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RefreshTokenCookieProvider {

    private final RefreshTokenProperties refreshTokenProperties;

    public RefreshTokenCookieProvider(RefreshTokenProperties refreshTokenProperties) {
        this.refreshTokenProperties = refreshTokenProperties;
    }

    public ResponseCookie createCookie(String refreshToken) {
        validateRefreshToken(refreshToken);
        return createCookieBuilder(refreshToken)
                .maxAge(refreshTokenProperties.expiration())
                .build();
    }

    public ResponseCookie deleteCookie() {
        return createCookieBuilder("")
                .maxAge(Duration.ZERO)
                .build();
    }

    private ResponseCookie.ResponseCookieBuilder createCookieBuilder(String value) {
        return ResponseCookie.from(refreshTokenProperties.cookieName(), value)
                .httpOnly(true)
                .secure(refreshTokenProperties.cookieSecure())
                .path(refreshTokenProperties.cookiePath())
                .sameSite(refreshTokenProperties.cookieSameSite());
    }

    private static void validateRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("Refresh Token 값은 필수입니다.");
        }
    }
}
