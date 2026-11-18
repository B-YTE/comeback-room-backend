package com.comebackroom.domain.auth.service;

import com.comebackroom.domain.auth.config.EmailVerificationProperties;
import com.comebackroom.domain.auth.enums.EmailVerificationPurpose;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class EmailVerificationHasher {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final SecretKeySpec secretKey;

    public EmailVerificationHasher(EmailVerificationProperties properties) {
        this.secretKey = new SecretKeySpec(
                properties.hashSecret().getBytes(StandardCharsets.UTF_8),
                HMAC_ALGORITHM
        );
    }

    public String hashVerificationCode(
            String email,
            EmailVerificationPurpose purpose,
            String verificationCode
    ) {
        return hash("code", email, purpose, verificationCode);
    }

    public String hashToken(
            String email,
            EmailVerificationPurpose purpose,
            String verificationToken
    ) {
        return hash("token", email, purpose, verificationToken);
    }

    public boolean matches(String expectedHash, String actualHash) {
        if (expectedHash == null || actualHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.US_ASCII),
                actualHash.getBytes(StandardCharsets.US_ASCII)
        );
    }

    private String hash(
            String valueType,
            String email,
            EmailVerificationPurpose purpose,
            String rawValue
    ) {
        validateInput(email, purpose, rawValue);
        String value = String.join(":", valueType, purpose.name(), email, rawValue);

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(secretKey);
            return HexFormat.of().formatHex(
                    mac.doFinal(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("이메일 인증 HMAC을 생성할 수 없습니다.", e);
        }
    }

    private static void validateInput(
            String email,
            EmailVerificationPurpose purpose,
            String rawValue
    ) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email 값은 필수입니다.");
        }
        if (purpose == null) {
            throw new IllegalArgumentException("purpose 값은 필수입니다.");
        }
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException("해시할 값은 필수입니다.");
        }
    }
}
