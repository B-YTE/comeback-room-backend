package com.comebackroom.domain.auth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("이메일 인증코드 생성기")
class EmailVerificationCodeGeneratorTest {

    @Test
    @DisplayName("앞자리 0을 포함할 수 있는 숫자 6자리 코드를 생성한다")
    void generateCode() {
        SecureRandom secureRandom = new FixedSecureRandom(42);
        EmailVerificationCodeGenerator generator = new EmailVerificationCodeGenerator(
                secureRandom
        );

        assertThat(generator.generateCode()).isEqualTo("000042");
    }

    private static class FixedSecureRandom extends SecureRandom {

        private final int value;

        private FixedSecureRandom(int value) {
            this.value = value;
        }

        @Override
        public int nextInt(int bound) {
            return value;
        }
    }
}
