package com.comebackroom.domain.auth.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayName("토큰 발급 결과 DTO")
class IssuedTokenResultTest {

    private static final String ACCESS_TOKEN = "access-token";
    private static final String REFRESH_TOKEN = "refresh-token";

    @Test
    @DisplayName("Access Token과 Refresh Token을 발급 결과로 생성한다")
    void createIssuedTokenResult() {
        IssuedTokenResult result = new IssuedTokenResult(ACCESS_TOKEN, REFRESH_TOKEN);

        assertAll(
                () -> assertThat(result.accessToken()).isEqualTo(ACCESS_TOKEN),
                () -> assertThat(result.refreshToken()).isEqualTo(REFRESH_TOKEN)
        );
    }

    @Test
    @DisplayName("필수 토큰이 없으면 발급 결과를 생성할 수 없다")
    void failToCreateIssuedTokenResultWithoutRequiredToken() {
        assertAll(
                () -> assertThatThrownBy(() -> new IssuedTokenResult(" ", REFRESH_TOKEN))
                        .isInstanceOf(IllegalArgumentException.class),
                () -> assertThatThrownBy(() -> new IssuedTokenResult(ACCESS_TOKEN, null))
                        .isInstanceOf(IllegalArgumentException.class)
        );
    }

    @Test
    @DisplayName("문자열 표현에 원본 토큰을 노출하지 않는다")
    void hideRawTokensFromToString() {
        IssuedTokenResult result = new IssuedTokenResult(ACCESS_TOKEN, REFRESH_TOKEN);

        assertThat(result.toString())
                .doesNotContain(ACCESS_TOKEN)
                .doesNotContain(REFRESH_TOKEN);
    }
}
