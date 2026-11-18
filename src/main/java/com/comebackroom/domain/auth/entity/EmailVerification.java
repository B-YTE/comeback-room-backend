package com.comebackroom.domain.auth.entity;

import com.comebackroom.domain.auth.enums.EmailVerificationPurpose;
import com.comebackroom.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "email_verifications",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_email_verifications_email_purpose",
                        columnNames = {"email", "purpose"}
                ),
                @UniqueConstraint(
                        name = "uk_email_verifications_token_hash",
                        columnNames = "verification_token_hash"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerification extends BaseTimeEntity {

    private static final int EMAIL_LENGTH = 255;
    private static final int PURPOSE_LENGTH = 30;
    private static final int HASH_LENGTH = 64;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", length = EMAIL_LENGTH, nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", length = PURPOSE_LENGTH, nullable = false)
    private EmailVerificationPurpose purpose;

    @Column(name = "code_hash", length = HASH_LENGTH, nullable = false)
    private String codeHash;

    @Column(name = "code_expires_at", nullable = false)
    private LocalDateTime codeExpiresAt;

    @Column(name = "verification_attempts", nullable = false)
    private int verificationAttempts;

    @Column(name = "code_consumed_at")
    private LocalDateTime codeConsumedAt;

    @Column(name = "request_count", nullable = false)
    private int requestCount;

    @Column(name = "request_window_started_at", nullable = false)
    private LocalDateTime requestWindowStartedAt;

    @Column(name = "last_sent_at", nullable = false)
    private LocalDateTime lastSentAt;

    @Column(name = "verification_token_hash", length = HASH_LENGTH)
    private String verificationTokenHash;

    @Column(name = "token_expires_at")
    private LocalDateTime tokenExpiresAt;

    @Column(name = "token_consumed_at")
    private LocalDateTime tokenConsumedAt;

    private EmailVerification(
            String email,
            EmailVerificationPurpose purpose,
            String codeHash,
            LocalDateTime codeExpiresAt,
            LocalDateTime issuedAt
    ) {
        validateEmail(email);
        validatePurpose(purpose);
        validateHash(codeHash, "codeHash");
        validateDateTime(codeExpiresAt, "codeExpiresAt");
        validateDateTime(issuedAt, "issuedAt");

        this.email = email;
        this.purpose = purpose;
        this.codeHash = codeHash;
        this.codeExpiresAt = codeExpiresAt;
        this.verificationAttempts = 0;
        this.requestCount = 1;
        this.requestWindowStartedAt = issuedAt;
        this.lastSentAt = issuedAt;
    }

    public static EmailVerification issue(
            String email,
            EmailVerificationPurpose purpose,
            String codeHash,
            LocalDateTime codeExpiresAt,
            LocalDateTime issuedAt
    ) {
        return new EmailVerification(email, purpose, codeHash, codeExpiresAt, issuedAt);
    }

    public boolean isInResendCooldown(LocalDateTime requestedAt, Duration resendCooldown) {
        validateDateTime(requestedAt, "requestedAt");
        return requestedAt.isBefore(lastSentAt.plus(resendCooldown));
    }

    public boolean hasReachedRequestLimit(
            LocalDateTime requestedAt,
            Duration requestLimitWindow,
            int maxRequestsPerWindow
    ) {
        validateDateTime(requestedAt, "requestedAt");

        return requestedAt.isBefore(requestWindowStartedAt.plus(requestLimitWindow))
                && requestCount >= maxRequestsPerWindow;
    }

    public void reissue(
            String newCodeHash,
            LocalDateTime newCodeExpiresAt,
            LocalDateTime issuedAt,
            Duration requestLimitWindow
    ) {
        validateHash(newCodeHash, "newCodeHash");
        validateDateTime(newCodeExpiresAt, "newCodeExpiresAt");
        validateDateTime(issuedAt, "issuedAt");

        if (!issuedAt.isBefore(requestWindowStartedAt.plus(requestLimitWindow))) {
            requestWindowStartedAt = issuedAt;
            requestCount = 0;
        }

        codeHash = newCodeHash;
        codeExpiresAt = newCodeExpiresAt;
        verificationAttempts = 0;
        codeConsumedAt = null;
        requestCount++;
        lastSentAt = issuedAt;
        verificationTokenHash = null;
        tokenExpiresAt = null;
        tokenConsumedAt = null;
    }

    public boolean canVerify(LocalDateTime verifiedAt, int maxVerificationAttempts) {
        validateDateTime(verifiedAt, "verifiedAt");
        return codeConsumedAt == null
                && codeExpiresAt.isAfter(verifiedAt)
                && verificationAttempts < maxVerificationAttempts;
    }

    public void recordFailedAttempt() {
        verificationAttempts++;
    }

    public void completeVerification(
            String newVerificationTokenHash,
            LocalDateTime newTokenExpiresAt,
            LocalDateTime verifiedAt
    ) {
        validateHash(newVerificationTokenHash, "newVerificationTokenHash");
        validateDateTime(newTokenExpiresAt, "newTokenExpiresAt");
        validateDateTime(verifiedAt, "verifiedAt");

        codeConsumedAt = verifiedAt;
        verificationTokenHash = newVerificationTokenHash;
        tokenExpiresAt = newTokenExpiresAt;
        tokenConsumedAt = null;
    }

    public boolean canConsumeToken(LocalDateTime consumedAt) {
        validateDateTime(consumedAt, "consumedAt");
        return verificationTokenHash != null
                && tokenConsumedAt == null
                && tokenExpiresAt.isAfter(consumedAt);
    }

    public void consumeToken(LocalDateTime consumedAt) {
        validateDateTime(consumedAt, "consumedAt");
        if (!canConsumeToken(consumedAt)) {
            throw new IllegalStateException("사용할 수 없는 이메일 인증 토큰입니다.");
        }
        tokenConsumedAt = consumedAt;
    }

    private static void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email 값은 필수입니다.");
        }
        if (email.length() > EMAIL_LENGTH) {
            throw new IllegalArgumentException("email은 255자 이하여야 합니다.");
        }
    }

    private static void validatePurpose(EmailVerificationPurpose purpose) {
        if (purpose == null) {
            throw new IllegalArgumentException("purpose 값은 필수입니다.");
        }
    }

    private static void validateHash(String hash, String fieldName) {
        if (hash == null || hash.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 값은 필수입니다.");
        }
        if (hash.length() != HASH_LENGTH) {
            throw new IllegalArgumentException(fieldName + "는 64자여야 합니다.");
        }
    }

    private static void validateDateTime(LocalDateTime dateTime, String fieldName) {
        if (dateTime == null) {
            throw new IllegalArgumentException(fieldName + " 값은 필수입니다.");
        }
    }
}
