package com.comebackroom.global.security.refresh;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayName("Refresh Token Cookie")
class RefreshTokenCookieProviderTest {

    private static final String REFRESH_TOKEN = "refresh-token";

    @Test
    @DisplayName("Refresh Token을 HttpOnly Cookie로 생성한다")
    void createCookie() {
        RefreshTokenCookieProvider cookieProvider = createCookieProvider(false, "Lax");

        ResponseCookie cookie = cookieProvider.createCookie(REFRESH_TOKEN);

        assertAll(
                () -> assertThat(cookie.getName()).isEqualTo("refreshToken"),
                () -> assertThat(cookie.getValue()).isEqualTo(REFRESH_TOKEN),
                () -> assertThat(cookie.isHttpOnly()).isTrue(),
                () -> assertThat(cookie.isSecure()).isFalse(),
                () -> assertThat(cookie.getPath()).isEqualTo("/api/v1/auth"),
                () -> assertThat(cookie.getSameSite()).isEqualTo("Lax"),
                () -> assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofDays(14))
        );
    }

    @Test
    @DisplayName("HTTPS 환경에서는 Secure Cookie를 생성한다")
    void createSecureCookie() {
        RefreshTokenCookieProvider cookieProvider = createCookieProvider(true, "None");

        ResponseCookie cookie = cookieProvider.createCookie(REFRESH_TOKEN);

        assertAll(
                () -> assertThat(cookie.isSecure()).isTrue(),
                () -> assertThat(cookie.getSameSite()).isEqualTo("None")
        );
    }

    @Test
    @DisplayName("Refresh Token Cookie를 즉시 만료시킨다")
    void deleteCookie() {
        RefreshTokenCookieProvider cookieProvider = createCookieProvider(false, "Lax");

        ResponseCookie cookie = cookieProvider.deleteCookie();

        assertAll(
                () -> assertThat(cookie.getValue()).isEmpty(),
                () -> assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO),
                () -> assertThat(cookie.isHttpOnly()).isTrue(),
                () -> assertThat(cookie.getPath()).isEqualTo("/api/v1/auth")
        );
    }

    @Test
    @DisplayName("Refresh Token 값이 없으면 Cookie를 생성할 수 없다")
    void rejectMissingToken() {
        RefreshTokenCookieProvider cookieProvider = createCookieProvider(false, "Lax");

        assertAll(
                () -> assertThatThrownBy(
                        () -> cookieProvider.createCookie(null)
                ).isInstanceOf(IllegalArgumentException.class),
                () -> assertThatThrownBy(
                        () -> cookieProvider.createCookie(" ")
                ).isInstanceOf(IllegalArgumentException.class)
        );
    }

    private RefreshTokenCookieProvider createCookieProvider(boolean secure, String sameSite) {
        RefreshTokenProperties properties = new RefreshTokenProperties(
                Duration.ofDays(14),
                "refreshToken",
                "/api/v1/auth",
                secure,
                sameSite
        );
        return new RefreshTokenCookieProvider(properties);
    }
}
