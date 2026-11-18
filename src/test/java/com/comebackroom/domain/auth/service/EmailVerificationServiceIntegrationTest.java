package com.comebackroom.domain.auth.service;

import com.comebackroom.TestcontainersConfiguration;
import com.comebackroom.domain.auth.dto.request.SendEmailVerificationCodeRequest;
import com.comebackroom.domain.auth.dto.request.VerifyEmailVerificationCodeRequest;
import com.comebackroom.domain.auth.entity.EmailVerification;
import com.comebackroom.domain.auth.enums.EmailVerificationPurpose;
import com.comebackroom.domain.auth.exception.AuthErrorCode;
import com.comebackroom.domain.auth.repository.EmailVerificationRepository;
import com.comebackroom.domain.member.entity.Member;
import com.comebackroom.domain.member.enums.Provider;
import com.comebackroom.domain.member.repository.MemberRepository;
import com.comebackroom.global.email.EmailDeliveryException;
import com.comebackroom.global.email.EmailSender;
import com.comebackroom.global.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

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

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private EmailVerificationCodeGenerator codeGenerator;

    @MockitoBean
    private EmailSender emailSender;

    private Member registeredMember;

    @BeforeEach
    void setUp() {
        emailVerificationRepository.deleteAll();
        given(codeGenerator.generateCode()).willReturn(VERIFICATION_CODE);
    }

    @AfterEach
    void deleteRegisteredMember() {
        if (registeredMember != null) {
            memberRepository.delete(registeredMember);
        }
    }

    @ParameterizedTest
    @EnumSource(Provider.class)
    @DisplayName("LOCAL과 GOOGLE 회원의 이메일은 대소문자와 무관하게 회원가입 인증 발급을 거절한다")
    void rejectSignUpForRegisteredMember(Provider provider) {
        createRegisteredMember(provider);

        assertThatThrownBy(() -> emailVerificationService.sendCode(sendRequest()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.DUPLICATE_EMAIL));

        assertThat(emailVerificationRepository.count()).isZero();
        then(emailSender).should(never()).send(any());
    }

    @ParameterizedTest
    @CsvSource({
            "LOCAL, FIND_ID",
            "LOCAL, PASSWORD_RESET",
            "GOOGLE, FIND_ID",
            "GOOGLE, PASSWORD_RESET"
    })
    @DisplayName("가입된 이메일도 계정 복구 목적의 인증코드는 발급할 수 있다")
    void allowRecoveryForRegisteredMember(Provider provider, EmailVerificationPurpose purpose) {
        createRegisteredMember(provider);

        emailVerificationService.sendCode(new SendEmailVerificationCodeRequest(EMAIL, purpose));

        assertThat(emailVerificationRepository.findByEmailAndPurpose(EMAIL, purpose)).isPresent();
        then(emailSender).should(times(1)).send(any());
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

    @Test
    @DisplayName("최초 발급 요청이 동시에 들어오면 하나만 발급하고 나머지는 요청 제한으로 처리한다")
    void serializeConcurrentFirstIssuance() throws Exception {
        CountDownLatch deliveryStarted = new CountDownLatch(1);
        CountDownLatch allowDeliveryCompletion = new CountDownLatch(1);
        willAnswer(invocation -> {
            deliveryStarted.countDown();
            if (!allowDeliveryCompletion.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("이메일 발송 대기시간을 초과했습니다.");
            }
            return null;
        }).given(emailSender).send(any());

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> firstRequest = submitSendRequest(executorService);
            assertThat(deliveryStarted.await(5, TimeUnit.SECONDS)).isTrue();

            Future<Throwable> secondRequest = submitSendRequest(executorService);
            waitUntilAdvisoryLockIsWaiting(Duration.ofSeconds(5));
            allowDeliveryCompletion.countDown();

            assertThat(firstRequest.get(5, TimeUnit.SECONDS)).isNull();
            assertThat(secondRequest.get(5, TimeUnit.SECONDS))
                    .isInstanceOfSatisfying(BusinessException.class, exception ->
                            assertThat(exception.getErrorCode()).isEqualTo(
                                    AuthErrorCode.EMAIL_REQUEST_LIMIT_EXCEEDED
                            ));
            assertThat(emailVerificationRepository.count()).isEqualTo(1);
            then(emailSender).should(times(1)).send(any());
        } finally {
            allowDeliveryCompletion.countDown();
            executorService.shutdownNow();
        }
    }

    private void createRegisteredMember(Provider provider) {
        Member member = switch (provider) {
            case LOCAL -> Member.createLocalMember(
                    "registered-member",
                    "encoded-password",
                    "ZIN@Example.COM"
            );
            case GOOGLE -> Member.createGoogleMember("ZIN@Example.COM", "google-member-id");
        };
        registeredMember = memberRepository.saveAndFlush(member);
    }

    private Future<Throwable> submitSendRequest(ExecutorService executorService) {
        return executorService.submit(() -> {
            try {
                emailVerificationService.sendCode(sendRequest());
                return null;
            } catch (Throwable throwable) {
                return throwable;
            }
        });
    }

    private void waitUntilAdvisoryLockIsWaiting(Duration timeout)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            Integer waitingTransactionCount = jdbcTemplate.queryForObject(
                    """
                            SELECT COUNT(*)
                            FROM pg_stat_activity
                            WHERE datname = current_database()
                              AND wait_event_type = 'Lock'
                              AND wait_event = 'advisory'
                            """,
                    Integer.class
            );
            if (waitingTransactionCount != null && waitingTransactionCount > 0) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("동시 요청이 advisory lock을 기다리지 않았습니다.");
    }

    private static SendEmailVerificationCodeRequest sendRequest() {
        return new SendEmailVerificationCodeRequest(
                EMAIL,
                EmailVerificationPurpose.SIGN_UP
        );
    }
}
