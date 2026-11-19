package com.comebackroom.domain.auth.entity;

import com.comebackroom.domain.auth.enums.EmailVerificationPurpose;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayName("이메일 인증 엔티티")
class EmailVerificationTest {

    private static final String EMAIL = "zin@example.com";
    private static final String CODE_HASH = "a".repeat(64);
    private static final String NEW_CODE_HASH = "b".repeat(64);
    private static final String TOKEN_HASH = "c".repeat(64);
    private static final LocalDateTime ISSUED_AT = LocalDateTime.of(2026, 11, 12, 12, 0);

    @Test
    @DisplayName("이메일과 목적에 연결된 인증코드를 발급한다")
    void issue() {
        EmailVerification emailVerification = createEmailVerification();

        assertAll(
                () -> assertThat(emailVerification.getEmail()).isEqualTo(EMAIL),
                () -> assertThat(emailVerification.getPurpose())
                        .isEqualTo(EmailVerificationPurpose.SIGN_UP),
                () -> assertThat(emailVerification.getCodeHash()).isEqualTo(CODE_HASH),
                () -> assertThat(emailVerification.getCodeExpiresAt())
                        .isEqualTo(ISSUED_AT.plusMinutes(5)),
                () -> assertThat(emailVerification.getVerificationAttempts()).isZero(),
                () -> assertThat(emailVerification.getRequestCount()).isEqualTo(1)
        );
    }

    @Test
    @DisplayName("재발송 대기시간 전에는 새 인증코드를 요청할 수 없다")
    void rejectRequestDuringCooldown() {
        EmailVerification emailVerification = createEmailVerification();

        assertAll(
                () -> assertThat(emailVerification.isInResendCooldown(
                        ISSUED_AT.plusSeconds(59),
                        Duration.ofMinutes(1)
                )).isTrue(),
                () -> assertThat(emailVerification.isInResendCooldown(
                        ISSUED_AT.plusMinutes(1),
                        Duration.ofMinutes(1)
                )).isFalse()
        );
    }

    @Test
    @DisplayName("요청 제한 구간이 지나면 요청 횟수를 새로 계산한다")
    void resetRequestCountAfterLimitWindow() {
        EmailVerification emailVerification = createEmailVerification();
        emailVerification.reissue(
                NEW_CODE_HASH,
                ISSUED_AT.plusMinutes(6),
                ISSUED_AT.plusMinutes(1),
                Duration.ofHours(1)
        );

        assertThat(emailVerification.hasReachedRequestLimit(
                ISSUED_AT.plusMinutes(2),
                Duration.ofHours(1),
                2
        )).isTrue();

        LocalDateTime nextWindow = ISSUED_AT.plusHours(1);
        assertThat(emailVerification.hasReachedRequestLimit(
                nextWindow,
                Duration.ofHours(1),
                2
        )).isFalse();

        emailVerification.reissue(
                CODE_HASH,
                nextWindow.plusMinutes(5),
                nextWindow,
                Duration.ofHours(1)
        );

        assertAll(
                () -> assertThat(emailVerification.getRequestCount()).isEqualTo(1),
                () -> assertThat(emailVerification.getRequestWindowStartedAt())
                        .isEqualTo(nextWindow)
        );
    }

    @Test
    @DisplayName("만료되거나 최대 검증 횟수에 도달한 인증코드는 검증할 수 없다")
    void rejectExpiredOrExhaustedCode() {
        EmailVerification emailVerification = createEmailVerification();

        assertThat(emailVerification.canVerify(ISSUED_AT.plusMinutes(5), 2)).isFalse();

        emailVerification.recordFailedAttempt();
        emailVerification.recordFailedAttempt();

        assertThat(emailVerification.canVerify(ISSUED_AT.plusMinutes(1), 2)).isFalse();
    }

    @Test
    @DisplayName("인증에 성공한 코드는 재사용할 수 없고 인증 토큰도 한 번만 소비할 수 있다")
    void consumeCodeAndTokenOnce() {
        EmailVerification emailVerification = createEmailVerification();
        LocalDateTime verifiedAt = ISSUED_AT.plusMinutes(1);
        emailVerification.completeVerification(
                TOKEN_HASH,
                verifiedAt.plusMinutes(30),
                verifiedAt
        );

        assertAll(
                () -> assertThat(emailVerification.canVerify(verifiedAt.plusSeconds(1), 5))
                        .isFalse(),
                () -> assertThat(emailVerification.canConsumeToken(verifiedAt.plusMinutes(1)))
                        .isTrue()
        );

        emailVerification.consumeToken(verifiedAt.plusMinutes(1));

        assertAll(
                () -> assertThat(emailVerification.canConsumeToken(verifiedAt.plusMinutes(2)))
                        .isFalse(),
                () -> assertThatThrownBy(
                        () -> emailVerification.consumeToken(verifiedAt.plusMinutes(2))
                ).isInstanceOf(IllegalStateException.class)
        );
    }

    private static EmailVerification createEmailVerification() {
        return EmailVerification.issue(
                EMAIL,
                EmailVerificationPurpose.SIGN_UP,
                CODE_HASH,
                ISSUED_AT.plusMinutes(5),
                ISSUED_AT
        );
    }
}
