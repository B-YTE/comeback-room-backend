package com.comebackroom.domain.auth.service;

import com.comebackroom.TestcontainersConfiguration;
import com.comebackroom.domain.auth.dto.request.SendEmailVerificationCodeRequest;
import com.comebackroom.domain.auth.dto.request.SignUpRequest;
import com.comebackroom.domain.auth.dto.request.VerifyEmailVerificationCodeRequest;
import com.comebackroom.domain.auth.entity.EmailVerification;
import com.comebackroom.domain.auth.enums.EmailVerificationPurpose;
import com.comebackroom.domain.auth.exception.AuthErrorCode;
import com.comebackroom.domain.auth.repository.EmailVerificationRepository;
import com.comebackroom.domain.member.entity.Member;
import com.comebackroom.domain.member.enums.Provider;
import com.comebackroom.domain.member.repository.MemberRepository;
import com.comebackroom.global.config.TimeZoneConfig;
import com.comebackroom.global.email.EmailDeliveryException;
import com.comebackroom.global.email.EmailMessage;
import com.comebackroom.global.email.EmailSender;
import com.comebackroom.global.exception.BusinessException;
import com.comebackroom.global.exception.GlobalErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
    private static final Instant NOW = Instant.parse("2026-11-18T03:00:00Z");

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private SignUpService signUpService;

    @Autowired
    private PasswordEncoder passwordEncoder;

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

    @MockitoBean
    private Clock clock;

    private Member registeredMember;

    @BeforeEach
    void setUp() {
        emailVerificationRepository.deleteAll();
        given(clock.instant()).willReturn(NOW);
        given(clock.getZone()).willReturn(TimeZoneConfig.APPLICATION_ZONE_ID);
        given(codeGenerator.generateCode()).willReturn(VERIFICATION_CODE);
    }

    @AfterEach
    void deleteRegisteredMember() {
        if (registeredMember != null) {
            memberRepository.delete(registeredMember);
        }
    }

    @Test
    void returnsCommonInputErrorForInvalidAvailabilityQueries() {
        assertThatThrownBy(() -> signUpService.isLoginIdAvailable("Zin123"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE));
        assertThatThrownBy(() -> signUpService.isNicknameAvailable("진"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE));
    }

    @Test
    void createsMemberAndProfileAndConsumesTokenOnlyOnce() {
        String token = issueSignUpToken();
        SignUpRequest request = signUpRequest(token, "퍼비");
        var response = signUpService.signUp(request);
        registeredMember = memberRepository.findById(response.memberId()).orElseThrow();

        try {
            assertThat(response.email()).isEqualTo(EMAIL);
            assertThat(response.nickname()).isEqualTo("퍼비");
            assertThat(passwordEncoder.matches(request.password(), registeredMember.getPassword())).isTrue();
            assertThat(signUpService.isLoginIdAvailable(request.loginId())).isFalse();
            assertThat(signUpService.isNicknameAvailable(request.nickname())).isFalse();
            assertThatThrownBy(() -> signUpService.signUp(request))
                    .isInstanceOfSatisfying(BusinessException.class, exception ->
                            assertThat(exception.getErrorCode()).isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE));
        } finally {
            jdbcTemplate.update("DELETE FROM profiles WHERE member_id = ?", response.memberId());
        }
    }

    @Test
    void duplicateNicknameDoesNotConsumeTokenOrCreateMember() {
        registeredMember = memberRepository.save(Member.createLocalMember(
                "existing", "stored-password", "existing@example.com"
        ));
        jdbcTemplate.update("INSERT INTO profiles (member_id, nickname) VALUES (?, ?)",
                registeredMember.getId(), "퍼비");

        try {
            String token = issueSignUpToken();
            assertThatThrownBy(() -> signUpService.signUp(signUpRequest(token, "퍼비")))
                    .isInstanceOfSatisfying(BusinessException.class, exception ->
                            assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.DUPLICATE_MEMBER));
            assertThat(memberRepository.existsByLoginId("zin123")).isFalse();
            assertThat(emailVerificationRepository.findByEmailAndPurpose(EMAIL, EmailVerificationPurpose.SIGN_UP)
                    .orElseThrow().getTokenConsumedAt()).isNull();
        } finally {
            jdbcTemplate.update("DELETE FROM profiles WHERE member_id = ?", registeredMember.getId());
        }
    }

    @Test
    void usesDifferentDeliveryKeysWhenTheSameCodeIsReissued() {
        emailVerificationService.sendCode(sendRequest());
        given(clock.instant()).willReturn(NOW.plus(Duration.ofMinutes(2)));
        emailVerificationService.sendCode(sendRequest());

        ArgumentCaptor<EmailMessage> messageCaptor = ArgumentCaptor.forClass(EmailMessage.class);
        then(emailSender).should(times(2)).send(messageCaptor.capture());
        assertThat(messageCaptor.getAllValues())
                .extracting(EmailMessage::idempotencyKey)
                .doesNotHaveDuplicates();
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
    @DisplayName("발송 실패 시에도 커밋한 코드와 횟수는 유지하고 즉시 재요청은 제한한다")
    void preserveIssuanceWhenDeliveryFails() {
        willThrow(new EmailDeliveryException("이메일 발송에 실패했습니다.", null))
                .given(emailSender)
                .send(any());

        assertThatThrownBy(() -> emailVerificationService.sendCode(sendRequest()))
                .isInstanceOf(EmailDeliveryException.class);

        EmailVerification emailVerification = emailVerificationRepository.findByEmailAndPurpose(
                EMAIL,
                EmailVerificationPurpose.SIGN_UP
        ).orElseThrow();
        assertThat(emailVerification.getRequestCount()).isEqualTo(1);
        assertThat(emailVerification.getCodeConsumedAt()).isNull();

        assertThatThrownBy(() -> emailVerificationService.sendCode(sendRequest()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.EMAIL_RESEND_TOO_SOON));
        then(emailSender).should(times(1)).send(any());

        // 타임아웃 후 실제 메일이 도착한 경우에도 저장된 코드로 인증할 수 있다.
        assertThat(emailVerificationService.verifyCode(new VerifyEmailVerificationCodeRequest(
                EMAIL, VERIFICATION_CODE, EmailVerificationPurpose.SIGN_UP
        )).verificationToken()).isNotBlank();
    }

    @Test
    @DisplayName("재발급 메일 발송이 실패해도 새 코드만 유지하고 기존 코드는 복원하지 않는다")
    void preserveReissuedCodeWhenDeliveryFails() {
        emailVerificationService.sendCode(sendRequest());
        given(clock.instant()).willReturn(NOW.plusSeconds(61));
        given(codeGenerator.generateCode()).willReturn("654321");
        willThrow(new EmailDeliveryException("이메일 발송에 실패했습니다.", null))
                .given(emailSender).send(any());

        assertThatThrownBy(() -> emailVerificationService.sendCode(sendRequest()))
                .isInstanceOf(EmailDeliveryException.class);

        EmailVerification emailVerification = emailVerificationRepository.findByEmailAndPurpose(
                EMAIL, EmailVerificationPurpose.SIGN_UP
        ).orElseThrow();
        assertThat(emailVerification.getRequestCount()).isEqualTo(2);
        assertThatThrownBy(() -> emailVerificationService.verifyCode(new VerifyEmailVerificationCodeRequest(
                EMAIL, VERIFICATION_CODE, EmailVerificationPurpose.SIGN_UP
        ))).isInstanceOf(BusinessException.class);
        assertThat(emailVerificationService.verifyCode(new VerifyEmailVerificationCodeRequest(
                EMAIL, "654321", EmailVerificationPurpose.SIGN_UP
        )).verificationToken()).isNotBlank();
    }

    @Test
    @DisplayName("메일 발송 전 DB가 커밋되고 발송 중에는 트랜잭션과 발급 락을 유지하지 않는다")
    void commitBeforeDeliveryAndReleaseLocks() throws Exception {
        CountDownLatch deliveryStarted = new CountDownLatch(1);
        CountDownLatch allowDeliveryCompletion = new CountDownLatch(1);
        willAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            deliveryStarted.countDown();
            if (!allowDeliveryCompletion.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("이메일 발송 대기시간을 초과했습니다.");
            }
            return null;
        }).given(emailSender).send(any());

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> firstRequest = submitSendRequest(executorService);
            assertThat(deliveryStarted.await(10, TimeUnit.SECONDS)).isTrue();

            // 다른 연결에서도 커밋된 발급 정보를 조회할 수 있다.
            assertThat(emailVerificationRepository.findByEmailAndPurpose(
                    EMAIL, EmailVerificationPurpose.SIGN_UP
            )).isPresent();
            Future<Throwable> secondRequest = submitSendRequest(executorService);
            assertThat(secondRequest.get(5, TimeUnit.SECONDS))
                    .isInstanceOfSatisfying(BusinessException.class, exception ->
                            assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.EMAIL_RESEND_TOO_SOON));
            assertThat(firstRequest.isDone()).isFalse();

            allowDeliveryCompletion.countDown();
            assertThat(firstRequest.get(5, TimeUnit.SECONDS)).isNull();
            assertThat(emailVerificationRepository.count()).isEqualTo(1);
            then(emailSender).should(times(1)).send(any());
        } finally {
            allowDeliveryCompletion.countDown();
            executorService.shutdownNow();
        }
    }

    @Test
    @DisplayName("최초 발급 요청이 동시에 들어오면 하나만 발급하고 나머지는 요청 제한으로 처리한다")
    void serializeConcurrentFirstIssuance() throws Exception {
        CountDownLatch issuanceStarted = new CountDownLatch(1);
        CountDownLatch allowIssuanceCompletion = new CountDownLatch(1);
        willAnswer(invocation -> {
            issuanceStarted.countDown();
            if (!allowIssuanceCompletion.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("인증코드 발급 대기시간을 초과했습니다.");
            }
            return VERIFICATION_CODE;
        }).given(codeGenerator).generateCode();

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> firstRequest = submitSendRequest(executorService);
            assertThat(issuanceStarted.await(10, TimeUnit.SECONDS)).isTrue();

            Future<Throwable> secondRequest = submitSendRequest(executorService);
            waitUntilAdvisoryLockIsWaiting(Duration.ofSeconds(5));
            allowIssuanceCompletion.countDown();

            assertThat(firstRequest.get(5, TimeUnit.SECONDS)).isNull();
            assertThat(secondRequest.get(5, TimeUnit.SECONDS))
                    .isInstanceOfSatisfying(BusinessException.class, exception ->
                            assertThat(exception.getErrorCode()).isEqualTo(
                                    AuthErrorCode.EMAIL_RESEND_TOO_SOON
                            ));
            assertThat(emailVerificationRepository.count()).isEqualTo(1);
            then(emailSender).should(times(1)).send(any());
        } finally {
            allowIssuanceCompletion.countDown();
            executorService.shutdownNow();
        }
    }

    private String issueSignUpToken() {
        emailVerificationService.sendCode(sendRequest());
        return emailVerificationService.verifyCode(new VerifyEmailVerificationCodeRequest(
                EMAIL, VERIFICATION_CODE, EmailVerificationPurpose.SIGN_UP
        )).verificationToken();
    }

    private SignUpRequest signUpRequest(String token, String nickname) {
        return new SignUpRequest(
                "zin123", "ZIN@EXAMPLE.COM", token, nickname, "password123!", "password123!", true, true
        );
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
