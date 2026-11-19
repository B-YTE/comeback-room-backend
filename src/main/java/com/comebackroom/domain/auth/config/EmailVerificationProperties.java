package com.comebackroom.domain.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@ConfigurationProperties(prefix = "app.email-verification")
public record EmailVerificationProperties(
        Duration codeExpiration,
        Duration resendCooldown,
        Duration requestLimitWindow,
        int maxRequestsPerWindow,
        int maxVerificationAttempts,
        Duration tokenExpiration,
        String hashSecret
) {

    private static final int MINIMUM_SECRET_LENGTH_BYTES = 32;

    public EmailVerificationProperties {
        validatePositiveDuration(codeExpiration, "인증코드 만료시간");
        validatePositiveDuration(resendCooldown, "인증코드 재발송 대기시간");
        validatePositiveDuration(requestLimitWindow, "인증코드 요청 제한 구간");
        validatePositiveNumber(maxRequestsPerWindow, "인증코드 최대 요청 횟수");
        validatePositiveNumber(maxVerificationAttempts, "인증코드 최대 검증 횟수");
        validatePositiveDuration(tokenExpiration, "이메일 인증 토큰 만료시간");

        if (hashSecret == null || hashSecret.isBlank()) {
            throw new IllegalArgumentException("이메일 인증 해시 Secret은 필수입니다.");
        }
        hashSecret = hashSecret.trim();
        if (hashSecret.getBytes(StandardCharsets.UTF_8).length < MINIMUM_SECRET_LENGTH_BYTES) {
            throw new IllegalArgumentException(
                    "이메일 인증 해시 Secret은 UTF-8 기준 32바이트 이상이어야 합니다."
            );
        }
    }

    private static void validatePositiveDuration(Duration duration, String fieldName) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(fieldName + "은 0보다 커야 합니다.");
        }
    }

    private static void validatePositiveNumber(int number, String fieldName) {
        if (number <= 0) {
            throw new IllegalArgumentException(fieldName + "는 0보다 커야 합니다.");
        }
    }
}
