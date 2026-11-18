package com.comebackroom.domain.auth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("이메일 인증 토큰 생성기")
class EmailVerificationTokenGeneratorTest {

    @Test
    @DisplayName("URL-safe Base64로 인코딩된 256비트 토큰을 생성한다")
    void generateToken() {
        EmailVerificationTokenGenerator generator = new EmailVerificationTokenGenerator(
                new SecureRandom()
        );

        String token = generator.generateToken();
        byte[] decodedToken = Base64.getUrlDecoder().decode(token);

        assertThat(decodedToken).hasSize(32);
    }
}
