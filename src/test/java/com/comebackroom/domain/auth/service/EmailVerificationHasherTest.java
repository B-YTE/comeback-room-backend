package com.comebackroom.domain.auth.service;

import com.comebackroom.domain.auth.config.EmailVerificationProperties;
import com.comebackroom.domain.auth.enums.EmailVerificationPurpose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayName("이메일 인증값 해시")
class EmailVerificationHasherTest {

    private EmailVerificationHasher hasher;

    @BeforeEach
    void setUp() {
        EmailVerificationProperties properties = new EmailVerificationProperties(
                Duration.ofMinutes(5),
                Duration.ofMinutes(1),
                Duration.ofHours(1),
                5,
                5,
                Duration.ofMinutes(30),
                "test-email-verification-secret-value-32-bytes"
        );
        hasher = new EmailVerificationHasher(properties);
    }

    @Test
    @DisplayName("같은 이메일·목적·코드는 같은 HMAC을 생성한다")
    void hashSameValue() {
        String firstHash = hasher.hashVerificationCode(
                "zin@example.com",
                EmailVerificationPurpose.SIGN_UP,
                "123456"
        );
        String secondHash = hasher.hashVerificationCode(
                "zin@example.com",
                EmailVerificationPurpose.SIGN_UP,
                "123456"
        );

        assertAll(
                () -> assertThat(firstHash).hasSize(64),
                () -> assertThat(firstHash).isEqualTo(secondHash),
                () -> assertThat(firstHash).doesNotContain("123456"),
                () -> assertThat(hasher.matches(firstHash, secondHash)).isTrue()
        );
    }

    @Test
    @DisplayName("인증코드는 이메일과 목적에 암호학적으로 연결된다")
    void bindHashToEmailAndPurpose() {
        String signUpHash = hasher.hashVerificationCode(
                "zin@example.com",
                EmailVerificationPurpose.SIGN_UP,
                "123456"
        );
        String otherEmailHash = hasher.hashVerificationCode(
                "other@example.com",
                EmailVerificationPurpose.SIGN_UP,
                "123456"
        );
        String otherPurposeHash = hasher.hashVerificationCode(
                "zin@example.com",
                EmailVerificationPurpose.FIND_ID,
                "123456"
        );

        assertThat(signUpHash)
                .isNotEqualTo(otherEmailHash)
                .isNotEqualTo(otherPurposeHash);
    }

    @Test
    @DisplayName("같은 원문이라도 인증코드와 인증 토큰은 다른 HMAC을 생성한다")
    void separateCodeAndTokenHashDomains() {
        String codeHash = hasher.hashVerificationCode(
                "zin@example.com",
                EmailVerificationPurpose.SIGN_UP,
                "same-value"
        );
        String tokenHash = hasher.hashToken(
                "zin@example.com",
                EmailVerificationPurpose.SIGN_UP,
                "same-value"
        );

        assertThat(codeHash).isNotEqualTo(tokenHash);
    }
}
