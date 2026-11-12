package com.comebackroom.global.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

@ConfigurationProperties(prefix = "app.email")
public record EmailProperties(
        EmailDeliveryMode deliveryMode,
        URI resendBaseUrl,
        String resendApiKey,
        String fromAddress
) {

    public EmailProperties {
        if (deliveryMode == null) {
            throw new IllegalArgumentException("이메일 발송 모드는 필수입니다.");
        }
        if (resendBaseUrl == null) {
            throw new IllegalArgumentException("Resend API Base URL은 필수입니다.");
        }

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
}
