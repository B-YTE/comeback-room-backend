package com.comebackroom.domain.auth.service;

import com.comebackroom.domain.auth.config.EmailVerificationProperties;
import com.comebackroom.domain.auth.dto.EmailVerificationTokenResult;
import com.comebackroom.domain.auth.dto.request.SendEmailVerificationCodeRequest;
import com.comebackroom.domain.auth.dto.request.VerifyEmailVerificationCodeRequest;
import com.comebackroom.domain.auth.entity.EmailVerification;
import com.comebackroom.domain.auth.enums.EmailVerificationPurpose;
import com.comebackroom.domain.auth.exception.AuthErrorCode;
import com.comebackroom.domain.auth.repository.EmailVerificationRepository;
import com.comebackroom.global.email.EmailMessage;
import com.comebackroom.global.email.EmailSender;
import com.comebackroom.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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

    private final EmailVerificationRepository emailVerificationRepository;
    private final EmailVerificationIssueService emailVerificationIssueService;
    private final EmailVerificationTokenGenerator tokenGenerator;
    private final EmailVerificationHasher hasher;
    private final EmailVerificationProperties properties;
    private final EmailSender emailSender;
    private final Clock clock;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void sendCode(SendEmailVerificationCodeRequest request) {
        String email = normalizeEmail(request.email());
        EmailVerificationPurpose purpose = request.purpose();
        EmailMessage emailMessage = emailVerificationIssueService.issueCode(email, purpose);

        // 발송 실패/타임아웃이어도 이미 커밋한 코드와 요청 제한은 유지한다.
        emailSender.send(emailMessage);

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

    private LocalDateTime currentDateTime() {
        return LocalDateTime.ofInstant(clock.instant(), APPLICATION_ZONE_ID);
    }

    static String normalizeEmail(String email) {
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
