package com.comebackroom.global.security.refresh;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Refresh Token 생성")
class RefreshTokenGeneratorTest {

    private final RefreshTokenGenerator refreshTokenGenerator =
            new RefreshTokenGenerator(new SecureRandom());

    @Test
    @DisplayName("256비트 URL-safe Refresh Token을 생성한다")
    void generateToken() {
        String refreshToken = refreshTokenGenerator.generateToken();

        assertThat(refreshToken)
                .hasSize(43)
                .matches("^[A-Za-z0-9_-]+$");
    }

    @Test
    @DisplayName("Refresh Token은 매번 새롭게 생성된다")
    void generateUniqueTokens() {
        String firstToken = refreshTokenGenerator.generateToken();
        String secondToken = refreshTokenGenerator.generateToken();

        assertThat(firstToken).isNotEqualTo(secondToken);
    }
}
