package com.comebackroom.domain.auth.service;

import com.comebackroom.TestcontainersConfiguration;
import com.comebackroom.domain.auth.dto.request.SendEmailVerificationCodeRequest;
import com.comebackroom.domain.auth.dto.request.VerifyEmailVerificationCodeRequest;
import com.comebackroom.domain.auth.entity.EmailVerification;
import com.comebackroom.domain.auth.enums.EmailVerificationPurpose;
import com.comebackroom.domain.auth.exception.AuthErrorCode;
import com.comebackroom.domain.auth.repository.EmailVerificationRepository;
import com.comebackroom.global.email.EmailDeliveryException;
import com.comebackroom.global.email.EmailSender;
import com.comebackroom.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@DisplayName("이메일 인증 서비스 트랜잭션")
class EmailVerificationServiceIntegrationTest {

    private static final String EMAIL = "zin@example.com";
    private static final String VERIFICATION_CODE = "123456";

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private EmailVerificationRepository emailVerificationRepository;

    @MockitoBean
    private EmailVerificationCodeGenerator codeGenerator;

    @MockitoBean
    private EmailSender emailSender;

    @BeforeEach
    void setUp() {
        emailVerificationRepository.deleteAll();
        given(codeGenerator.generateCode()).willReturn(VERIFICATION_CODE);
    }

    @Test
    @DisplayName("잘못된 인증코드의 실패 횟수는 비즈니스 예외 이후에도 저장된다")
    void persistFailedVerificationAttempt() {
        emailVerificationService.sendCode(sendRequest());

        assertThatThrownBy(() -> emailVerificationService.verifyCode(
                new VerifyEmailVerificationCodeRequest(
                        EMAIL,
                        "999999",
                        EmailVerificationPurpose.SIGN_UP
                )
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_VERIFICATION_CODE));

        EmailVerification emailVerification = emailVerificationRepository
                .findByEmailAndPurpose(EMAIL, EmailVerificationPurpose.SIGN_UP)
                .orElseThrow();
        assertThat(emailVerification.getVerificationAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("메일 발송이 실패하면 발급 정보 저장도 롤백한다")
    void rollBackIssuanceWhenDeliveryFails() {
        willThrow(new EmailDeliveryException("이메일 발송에 실패했습니다.", null))
                .given(emailSender)
                .send(any());

        assertThatThrownBy(() -> emailVerificationService.sendCode(sendRequest()))
                .isInstanceOf(EmailDeliveryException.class);

        assertThat(emailVerificationRepository.findByEmailAndPurpose(
                EMAIL,
                EmailVerificationPurpose.SIGN_UP
        )).isEmpty();
    }

    private static SendEmailVerificationCodeRequest sendRequest() {
        return new SendEmailVerificationCodeRequest(
                EMAIL,
                EmailVerificationPurpose.SIGN_UP
        );
    }
}
