package com.comebackroom.global.security.refresh;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayName("Refresh Token 설정")
class RefreshTokenPropertiesTest {

    @Test
    @DisplayName("Refresh Token과 Cookie 설정을 생성한다")
    void createProperties() {
        RefreshTokenProperties properties = createProperties(false, "lax");

        assertAll(
                () -> assertThat(properties.expiration()).isEqualTo(Duration.ofDays(14)),
                () -> assertThat(properties.cookieName()).isEqualTo("refreshToken"),
                () -> assertThat(properties.cookiePath()).isEqualTo("/api/v1/auth"),
                () -> assertThat(properties.cookieSecure()).isFalse(),
                () -> assertThat(properties.cookieSameSite()).isEqualTo("Lax")
        );
    }

    @Test
    @DisplayName("Refresh Token 만료시간은 0보다 커야 한다")
    void rejectInvalidExpiration() {
        assertAll(
                () -> assertThatThrownBy(
                        () -> new RefreshTokenProperties(
                                Duration.ZERO,
                                "refreshToken",
                                "/api/v1/auth",
                                false,
                                "Lax"
                        )
                ).isInstanceOf(IllegalArgumentException.class),
                () -> assertThatThrownBy(
                        () -> new RefreshTokenProperties(
                                Duration.ofDays(-1),
                                "refreshToken",
                                "/api/v1/auth",
                                false,
                                "Lax"
                        )
                ).isInstanceOf(IllegalArgumentException.class)
        );
    }

    @Test
    @DisplayName("Cookie 경로는 /로 시작해야 한다")
    void rejectInvalidCookiePath() {
        assertThatThrownBy(
                () -> new RefreshTokenProperties(
                        Duration.ofDays(14),
                        "refreshToken",
                        "api/v1/auth",
                        false,
                        "Lax"
                )
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Refresh Token Cookie 경로는 /로 시작해야 합니다.");
    }

    @Test
    @DisplayName("Cookie SameSite 값은 Strict, Lax, None만 사용할 수 있다")
    void rejectInvalidSameSite() {
        assertThatThrownBy(
                () -> new RefreshTokenProperties(
                        Duration.ofDays(14),
                        "refreshToken",
                        "/api/v1/auth",
                        false,
                        "invalid"
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("SameSite=None은 Secure Cookie에서만 사용할 수 있다")
    void requireSecureCookieForSameSiteNone() {
        assertThatThrownBy(
                () -> createProperties(false, "None")
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("SameSite=None은 Secure Cookie 설정이 필요합니다.");

        assertThat(createProperties(true, "None").cookieSameSite()).isEqualTo("None");
    }

    private RefreshTokenProperties createProperties(boolean secure, String sameSite) {
        return new RefreshTokenProperties(
                Duration.ofDays(14),
                "refreshToken",
                "/api/v1/auth",
                secure,
                sameSite
        );
    }
}
