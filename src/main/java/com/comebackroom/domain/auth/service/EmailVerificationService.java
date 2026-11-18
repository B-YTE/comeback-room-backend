package com.comebackroom.domain.auth.service;

import com.comebackroom.domain.auth.config.EmailVerificationProperties;
import com.comebackroom.domain.auth.dto.EmailVerificationTokenResult;
import com.comebackroom.domain.auth.dto.request.SendEmailVerificationCodeRequest;
import com.comebackroom.domain.auth.dto.request.VerifyEmailVerificationCodeRequest;
import com.comebackroom.domain.auth.entity.EmailVerification;
import com.comebackroom.domain.auth.enums.EmailVerificationPurpose;
import com.comebackroom.domain.auth.exception.AuthErrorCode;
import com.comebackroom.domain.auth.repository.EmailVerificationRepository;
import com.comebackroom.domain.member.repository.MemberRepository;
import com.comebackroom.global.email.EmailMessage;
import com.comebackroom.global.email.EmailSender;
import com.comebackroom.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final ZoneId APPLICATION_ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final String EMAIL_SUBJECT = "[Comeback Room] 이메일 인증코드";

    private final EmailVerificationRepository emailVerificationRepository;
    private final MemberRepository memberRepository;
    private final EmailVerificationCodeGenerator codeGenerator;
    private final EmailVerificationTokenGenerator tokenGenerator;
    private final EmailVerificationHasher hasher;
    private final EmailVerificationProperties properties;
    private final EmailSender emailSender;
    private final Clock clock;

    @Transactional
    public void sendCode(SendEmailVerificationCodeRequest request) {
        String email = normalizeEmail(request.email());
        EmailVerificationPurpose purpose = request.purpose();
        validateSignUpEmailAvailability(email, purpose);
        emailVerificationRepository.acquireTransactionLock(email, purpose.name());

        LocalDateTime issuedAt = currentDateTime();
        String verificationCode = codeGenerator.generateCode();
        String codeHash = hasher.hashVerificationCode(email, purpose, verificationCode);

        EmailVerification emailVerification = emailVerificationRepository
                .findWithLockByEmailAndPurpose(email, purpose)
                .map(existingVerification -> reissue(
                        existingVerification,
                        codeHash,
                        issuedAt
                ))
                .orElseGet(() -> EmailVerification.issue(
                        email,
                        purpose,
                        codeHash,
                        issuedAt.plus(properties.codeExpiration()),
                        issuedAt
                ));

        emailVerificationRepository.saveAndFlush(emailVerification);
        emailSender.send(createEmailMessage(
                email,
                purpose,
                verificationCode,
                codeHash
        ));

        log.info(
                "이메일 인증코드 발급 완료 email={} purpose={}",
                maskEmail(email),
                purpose
        );
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public EmailVerificationTokenResult verifyCode(VerifyEmailVerificationCodeRequest request) {
        String email = normalizeEmail(request.email());
        EmailVerificationPurpose purpose = request.purpose();
        LocalDateTime verifiedAt = currentDateTime();
        EmailVerification emailVerification = getVerifiableEmailVerification(
                email,
                purpose,
                verifiedAt
        );

        String requestedCodeHash = hasher.hashVerificationCode(
                email,
                purpose,
                request.verificationCode()
        );
        if (!hasher.matches(emailVerification.getCodeHash(), requestedCodeHash)) {
            emailVerification.recordFailedAttempt();
            emailVerificationRepository.flush();
            throw new BusinessException(AuthErrorCode.INVALID_VERIFICATION_CODE);
        }

        String verificationToken = tokenGenerator.generateToken();
        String verificationTokenHash = hasher.hashToken(email, purpose, verificationToken);
        emailVerification.completeVerification(
                verificationTokenHash,
                verifiedAt.plus(properties.tokenExpiration()),
                verifiedAt
        );
        emailVerificationRepository.flush();

        log.info(
                "이메일 인증 완료 email={} purpose={}",
                maskEmail(email),
                purpose
        );
        return new EmailVerificationTokenResult(verificationToken);
    }

    private void validateSignUpEmailAvailability(String email, EmailVerificationPurpose purpose) {
        if (purpose == EmailVerificationPurpose.SIGN_UP
                && memberRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException(AuthErrorCode.DUPLICATE_EMAIL);
        }
    }

    private EmailVerification reissue(
            EmailVerification emailVerification,
            String codeHash,
            LocalDateTime issuedAt
    ) {
        if (!emailVerification.canRequest(
                issuedAt,
                properties.resendCooldown(),
                properties.requestLimitWindow(),
                properties.maxRequestsPerWindow()
        )) {
            throw new BusinessException(AuthErrorCode.EMAIL_REQUEST_LIMIT_EXCEEDED);
        }

        emailVerification.reissue(
                codeHash,
                issuedAt.plus(properties.codeExpiration()),
                issuedAt,
                properties.requestLimitWindow()
        );
        return emailVerification;
    }

    private EmailVerification getVerifiableEmailVerification(
            String email,
            EmailVerificationPurpose purpose,
            LocalDateTime verifiedAt
    ) {
        EmailVerification emailVerification = emailVerificationRepository
                .findWithLockByEmailAndPurpose(email, purpose)
                .orElseThrow(() -> new BusinessException(
                        AuthErrorCode.INVALID_VERIFICATION_CODE
                ));

        if (!emailVerification.canVerify(
                verifiedAt,
                properties.maxVerificationAttempts()
        )) {
            throw new BusinessException(AuthErrorCode.INVALID_VERIFICATION_CODE);
        }
        return emailVerification;
    }

    private EmailMessage createEmailMessage(
            String email,
            EmailVerificationPurpose purpose,
            String verificationCode,
            String codeHash
    ) {
        String text = """
                Comeback Room %s 이메일 인증코드는 %s입니다.
                인증코드는 %d분 동안 유효합니다.
                본인이 요청하지 않았다면 이 메일을 무시해주세요.
                """.formatted(
                purpose.getDescription(),
                verificationCode,
                properties.codeExpiration().toMinutes()
        );

        return new EmailMessage(
                email,
                EMAIL_SUBJECT,
                text,
                "email-verification/" + codeHash
        );
    }

    private LocalDateTime currentDateTime() {
        return LocalDateTime.ofInstant(clock.instant(), APPLICATION_ZONE_ID);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return "***" + email.substring(Math.max(atIndex, 0));
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }
}
