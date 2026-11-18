package com.comebackroom.global.email;

public record EmailMessage(
        String recipient,
        String subject,
        String text,
        String idempotencyKey
) {

    public EmailMessage {
        recipient = requireText(recipient, "이메일 수신자는 필수입니다.");
        subject = requireText(subject, "이메일 제목은 필수입니다.");
        text = requireText(text, "이메일 본문은 필수입니다.");
        idempotencyKey = requireText(idempotencyKey, "이메일 멱등성 키는 필수입니다.");
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
