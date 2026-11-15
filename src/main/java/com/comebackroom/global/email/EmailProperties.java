package com.comebackroom.global.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "app.email")
public record EmailProperties(
        EmailDeliveryMode deliveryMode,
        URI resendBaseUrl,
        String resendApiKey,
        String fromAddress,
        Duration connectTimeout,
        Duration readTimeout
) {

    public EmailProperties {
        if (deliveryMode == null) {
            throw new IllegalArgumentException("이메일 발송 모드는 필수입니다.");
        }
        if (resendBaseUrl == null) {
            throw new IllegalArgumentException("Resend API Base URL은 필수입니다.");
        }
        validatePositiveDuration(connectTimeout, "이메일 API 연결 제한시간");
        validatePositiveDuration(readTimeout, "이메일 API 응답 제한시간");

        resendApiKey = trimToEmpty(resendApiKey);
        fromAddress = trimToEmpty(fromAddress);

        if (deliveryMode == EmailDeliveryMode.RESEND) {
            requireText(resendApiKey, "Resend API Key는 필수입니다.");
            requireText(fromAddress, "이메일 발신 주소는 필수입니다.");
            if (!"https".equalsIgnoreCase(resendBaseUrl.getScheme())) {
                throw new IllegalArgumentException("Resend API Base URL은 HTTPS여야 합니다.");
            }
        }
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static void requireText(String value, String message) {
        if (value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void validatePositiveDuration(Duration duration, String fieldName) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(fieldName + "은 0보다 커야 합니다.");
        }
    }
}
