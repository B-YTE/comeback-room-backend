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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("이메일 인증 서비스")
class EmailVerificationServiceTest {

    private static final String EMAIL = "zin@example.com";
    private static final String VERIFICATION_CODE = "123456";
    private static final String VERIFICATION_TOKEN = "email-verification-token";
    private static final Instant NOW = Instant.parse("2026-11-12T03:00:00Z");
    private static final LocalDateTime CURRENT_DATE_TIME = LocalDateTime.of(
            2026,
            11,
            12,
            12,
            0
    );

    @Mock
    private EmailVerificationRepository emailVerificationRepository;

    @Mock
    private EmailVerificationCodeGenerator codeGenerator;

    @Mock
    private EmailVerificationTokenGenerator tokenGenerator;

    @Mock
    private EmailSender emailSender;

    private EmailVerificationProperties properties;
    private EmailVerificationHasher hasher;
    private EmailVerificationService emailVerificationService;

    @BeforeEach
    void setUp() {
        properties = new EmailVerificationProperties(
                Duration.ofMinutes(5),
                Duration.ofMinutes(1),
                Duration.ofHours(1),
                5,
                5,
                Duration.ofMinutes(30),
                "test-email-verification-secret-value-32-bytes"
        );
        hasher = new EmailVerificationHasher(properties);
        emailVerificationService = new EmailVerificationService(
                emailVerificationRepository,
                codeGenerator,
                tokenGenerator,
                hasher,
                properties,
                emailSender,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    @DisplayName("인증코드를 생성해 해시만 저장한 뒤 이메일로 원문 코드를 발송한다")
    void sendCode() {
        given(codeGenerator.generateCode()).willReturn(VERIFICATION_CODE);
        given(emailVerificationRepository.findWithLockByEmailAndPurpose(
                EMAIL,
                EmailVerificationPurpose.SIGN_UP
        )).willReturn(Optional.empty());

        emailVerificationService.sendCode(new SendEmailVerificationCodeRequest(
                " ZIN@example.com ",
                EmailVerificationPurpose.SIGN_UP
        ));

        ArgumentCaptor<EmailVerification> verificationCaptor = ArgumentCaptor.forClass(
                EmailVerification.class
        );
        ArgumentCaptor<EmailMessage> messageCaptor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailVerificationRepository).saveAndFlush(verificationCaptor.capture());
        verify(emailSender).send(messageCaptor.capture());

        EmailVerification savedVerification = verificationCaptor.getValue();
        EmailMessage sentMessage = messageCaptor.getValue();
        String expectedCodeHash = hasher.hashVerificationCode(
                EMAIL,
                EmailVerificationPurpose.SIGN_UP,
                VERIFICATION_CODE
        );

        assertAll(
                () -> assertThat(savedVerification.getEmail()).isEqualTo(EMAIL),
                () -> assertThat(savedVerification.getCodeHash()).isEqualTo(expectedCodeHash),
                () -> assertThat(savedVerification.getCodeHash())
                        .doesNotContain(VERIFICATION_CODE),
                () -> assertThat(savedVerification.getCodeExpiresAt())
                        .isEqualTo(CURRENT_DATE_TIME.plusMinutes(5)),
                () -> assertThat(sentMessage.recipient()).isEqualTo(EMAIL),
                () -> assertThat(sentMessage.text()).contains(VERIFICATION_CODE),
                () -> assertThat(sentMessage.idempotencyKey())
                        .isEqualTo("email-verification/" + expectedCodeHash)
        );

        InOrder callOrder = inOrder(emailVerificationRepository, emailSender);
        callOrder.verify(emailVerificationRepository).saveAndFlush(savedVerification);
        callOrder.verify(emailSender).send(sentMessage);
    }

    @Test
    @DisplayName("재발송 대기시간 내의 요청은 429 오류로 거절한다")
    void rejectRequestDuringCooldown() {
        EmailVerification emailVerification = createEmailVerification(CURRENT_DATE_TIME);
        given(codeGenerator.generateCode()).willReturn(VERIFICATION_CODE);
        given(emailVerificationRepository.findWithLockByEmailAndPurpose(
                EMAIL,
                EmailVerificationPurpose.SIGN_UP
        )).willReturn(Optional.of(emailVerification));

        assertErrorCode(
                () -> emailVerificationService.sendCode(sendRequest()),
                AuthErrorCode.EMAIL_REQUEST_LIMIT_EXCEEDED
        );

        verify(emailVerificationRepository, never()).saveAndFlush(emailVerification);
        verify(emailSender, never()).send(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("한 시간 동안 다섯 번 요청한 이메일은 추가 발급을 거절한다")
    void rejectRequestOverWindowLimit() {
        LocalDateTime firstIssuedAt = CURRENT_DATE_TIME.minusMinutes(5);
        EmailVerification emailVerification = createEmailVerification(firstIssuedAt);
        for (int minute = 4; minute >= 1; minute--) {
            LocalDateTime issuedAt = CURRENT_DATE_TIME.minusMinutes(minute);
            emailVerification.reissue(
                    "b".repeat(64),
                    issuedAt.plusMinutes(5),
                    issuedAt,
                    properties.requestLimitWindow()
            );
        }
        given(codeGenerator.generateCode()).willReturn(VERIFICATION_CODE);
        given(emailVerificationRepository.findWithLockByEmailAndPurpose(
                EMAIL,
                EmailVerificationPurpose.SIGN_UP
        )).willReturn(Optional.of(emailVerification));

        assertErrorCode(
                () -> emailVerificationService.sendCode(sendRequest()),
                AuthErrorCode.EMAIL_REQUEST_LIMIT_EXCEEDED
        );

        assertThat(emailVerification.getRequestCount()).isEqualTo(5);
        verify(emailSender, never()).send(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("올바른 인증코드를 일회성 토큰으로 교환하고 토큰 해시만 저장한다")
    void verifyCode() {
        EmailVerification emailVerification = createEmailVerification(
                CURRENT_DATE_TIME.minusMinutes(1)
        );
        given(emailVerificationRepository.findWithLockByEmailAndPurpose(
                EMAIL,
                EmailVerificationPurpose.SIGN_UP
        )).willReturn(Optional.of(emailVerification));
        given(tokenGenerator.generateToken()).willReturn(VERIFICATION_TOKEN);

        EmailVerificationTokenResult result = emailVerificationService.verifyCode(
                verifyRequest(VERIFICATION_CODE, EmailVerificationPurpose.SIGN_UP)
        );

        String expectedTokenHash = hasher.hashToken(
                EMAIL,
                EmailVerificationPurpose.SIGN_UP,
                VERIFICATION_TOKEN
        );
        assertAll(
                () -> assertThat(result.verificationToken()).isEqualTo(VERIFICATION_TOKEN),
                () -> assertThat(emailVerification.getCodeConsumedAt())
                        .isEqualTo(CURRENT_DATE_TIME),
                () -> assertThat(emailVerification.getVerificationTokenHash())
                        .isEqualTo(expectedTokenHash),
                () -> assertThat(emailVerification.getVerificationTokenHash())
                        .doesNotContain(VERIFICATION_TOKEN),
                () -> assertThat(emailVerification.getTokenExpiresAt())
                        .isEqualTo(CURRENT_DATE_TIME.plusMinutes(30))
        );
        verify(emailVerificationRepository).flush();
    }

    @Test
    @DisplayName("잘못된 인증코드는 실패 횟수를 증가시키고 400 오류를 반환한다")
    void rejectWrongVerificationCode() {
        EmailVerification emailVerification = createEmailVerification(
                CURRENT_DATE_TIME.minusMinutes(1)
        );
        given(emailVerificationRepository.findWithLockByEmailAndPurpose(
                EMAIL,
                EmailVerificationPurpose.SIGN_UP
        )).willReturn(Optional.of(emailVerification));

        assertErrorCode(
                () -> emailVerificationService.verifyCode(
                        verifyRequest("999999", EmailVerificationPurpose.SIGN_UP)
                ),
                AuthErrorCode.INVALID_VERIFICATION_CODE
        );

        assertThat(emailVerification.getVerificationAttempts()).isEqualTo(1);
        verify(emailVerificationRepository).flush();
        verify(tokenGenerator, never()).generateToken();
    }

    @Test
    @DisplayName("만료된 인증코드는 검증할 수 없다")
    void rejectExpiredVerificationCode() {
        EmailVerification expiredVerification = createEmailVerification(
                CURRENT_DATE_TIME.minusMinutes(5)
        );
        given(emailVerificationRepository.findWithLockByEmailAndPurpose(
                EMAIL,
                EmailVerificationPurpose.SIGN_UP
        )).willReturn(Optional.of(expiredVerification));

        assertErrorCode(
                () -> emailVerificationService.verifyCode(
                        verifyRequest(VERIFICATION_CODE, EmailVerificationPurpose.SIGN_UP)
                ),
                AuthErrorCode.INVALID_VERIFICATION_CODE
        );

        verify(tokenGenerator, never()).generateToken();
    }

    @Test
    @DisplayName("인증코드는 발급받은 목적과 다른 목적으로 검증할 수 없다")
    void bindVerificationCodeToPurpose() {
        given(emailVerificationRepository.findWithLockByEmailAndPurpose(
                EMAIL,
                EmailVerificationPurpose.FIND_ID
        )).willReturn(Optional.empty());

        assertErrorCode(
                () -> emailVerificationService.verifyCode(
                        verifyRequest(VERIFICATION_CODE, EmailVerificationPurpose.FIND_ID)
                ),
                AuthErrorCode.INVALID_VERIFICATION_CODE
        );

        verify(tokenGenerator, never()).generateToken();
    }

    private EmailVerification createEmailVerification(LocalDateTime issuedAt) {
        return EmailVerification.issue(
                EMAIL,
                EmailVerificationPurpose.SIGN_UP,
                hasher.hashVerificationCode(
                        EMAIL,
                        EmailVerificationPurpose.SIGN_UP,
                        VERIFICATION_CODE
                ),
                issuedAt.plus(properties.codeExpiration()),
                issuedAt
        );
    }

    private static SendEmailVerificationCodeRequest sendRequest() {
        return new SendEmailVerificationCodeRequest(
                EMAIL,
                EmailVerificationPurpose.SIGN_UP
        );
    }

    private static VerifyEmailVerificationCodeRequest verifyRequest(
            String verificationCode,
            EmailVerificationPurpose purpose
    ) {
        return new VerifyEmailVerificationCodeRequest(EMAIL, verificationCode, purpose);
    }

    private static void assertErrorCode(Runnable action, AuthErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }
}
