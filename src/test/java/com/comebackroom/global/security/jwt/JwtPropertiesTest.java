package com.comebackroom.global.security.jwt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JWT 설정")
class JwtPropertiesTest {

    private static final String VALID_SECRET =
            "Y2hhbmdlLW1lLWJlZm9yZS11c2luZy1pbi1wcm9kdWN0aW9uIQ==";

    @Test
    @DisplayName("32바이트 이상의 Base64 secret과 양수 만료시간을 허용한다")
    void createWithValidProperties() {
        assertThatNoException().isThrownBy(
                () -> new JwtProperties(VALID_SECRET, Duration.ofMinutes(30))
        );
    }

    @Test
    @DisplayName("secret이 없으면 생성할 수 없다")
    void failToCreateWithoutSecret() {
        assertThatThrownBy(() -> new JwtProperties(" ", Duration.ofMinutes(30)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JWT secret 값은 필수입니다.");
    }

    @Test
    @DisplayName("secret이 Base64 형식이 아니면 생성할 수 없다")
    void failToCreateWithInvalidBase64Secret() {
        assertThatThrownBy(() -> new JwtProperties("not-base64!", Duration.ofMinutes(30)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JWT secret은 올바른 Base64 형식이어야 합니다.");
    }

    @Test
    @DisplayName("디코딩한 secret이 32바이트 미만이면 생성할 수 없다")
    void failToCreateWithShortSecret() {
        assertThatThrownBy(() -> new JwtProperties("c2hvcnQ=", Duration.ofMinutes(30)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JWT secret은 Base64 디코딩 기준 32바이트 이상이어야 합니다.");
    }

    @Test
    @DisplayName("Access Token 만료시간이 0 이하면 생성할 수 없다")
    void failToCreateWithNonPositiveExpiration() {
        assertThatThrownBy(() -> new JwtProperties(VALID_SECRET, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Access Token 만료시간은 0보다 커야 합니다.");
    }
}
