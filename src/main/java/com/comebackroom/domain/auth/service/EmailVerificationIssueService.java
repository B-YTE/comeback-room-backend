package com.comebackroom.domain.auth.service;

import com.comebackroom.domain.auth.config.EmailVerificationProperties;
import com.comebackroom.domain.auth.entity.EmailVerification;
import com.comebackroom.domain.auth.enums.EmailVerificationPurpose;
import com.comebackroom.domain.auth.exception.AuthErrorCode;
import com.comebackroom.domain.auth.repository.EmailVerificationRepository;
import com.comebackroom.domain.member.repository.MemberRepository;
import com.comebackroom.global.email.EmailMessage;
import com.comebackroom.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class EmailVerificationIssueService {

    private static final ZoneId APPLICATION_ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final String EMAIL_SUBJECT = "[Comeback Room] 이메일 인증코드";

    private final EmailVerificationRepository emailVerificationRepository;
    private final MemberRepository memberRepository;
    private final EmailVerificationCodeGenerator codeGenerator;
    private final EmailVerificationHasher hasher;
    private final EmailVerificationProperties properties;
    private final Clock clock;

    // 별도 Bean의 프록시를 거쳐 호출하므로 반환 전에 발급 정보가 커밋된다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EmailMessage issueCode(String email, EmailVerificationPurpose purpose) {
        validateSignUpEmailAvailability(email, purpose);
        emailVerificationRepository.acquireTransactionLock(email, purpose.name());

        LocalDateTime issuedAt = LocalDateTime.ofInstant(clock.instant(), APPLICATION_ZONE_ID);
        String verificationCode = codeGenerator.generateCode();
        String codeHash = hasher.hashVerificationCode(email, purpose, verificationCode);

        EmailVerification emailVerification = emailVerificationRepository
                .findWithLockByEmailAndPurpose(email, purpose)
                .map(existingVerification -> reissue(existingVerification, codeHash, issuedAt))
                .orElseGet(() -> EmailVerification.issue(
                        email,
                        purpose,
                        codeHash,
                        issuedAt.plus(properties.codeExpiration()),
                        issuedAt
                ));

        emailVerificationRepository.saveAndFlush(emailVerification);
        return createEmailMessage(email, purpose, verificationCode, codeHash);
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
        // 두 제한에 동시에 걸리면 더 긴 대기가 필요한 시간당 한도를 우선한다.
        if (emailVerification.hasReachedRequestLimit(
                issuedAt,
                properties.requestLimitWindow(),
                properties.maxRequestsPerWindow()
        )) {
            throw new BusinessException(AuthErrorCode.EMAIL_REQUEST_LIMIT_EXCEEDED);
        }
        if (emailVerification.isInResendCooldown(issuedAt, properties.resendCooldown())) {
            throw new BusinessException(AuthErrorCode.EMAIL_RESEND_TOO_SOON);
        }

        emailVerification.reissue(
                codeHash,
                issuedAt.plus(properties.codeExpiration()),
                issuedAt,
                properties.requestLimitWindow()
        );
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

        return new EmailMessage(email, EMAIL_SUBJECT, text, "email-verification/" + codeHash);
    }
}
