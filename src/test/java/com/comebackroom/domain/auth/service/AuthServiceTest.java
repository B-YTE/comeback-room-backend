package com.comebackroom.domain.auth.service;

import com.comebackroom.domain.auth.dto.IssuedTokenResult;
import com.comebackroom.domain.auth.dto.request.LoginRequest;
import com.comebackroom.domain.auth.entity.RefreshToken;
import com.comebackroom.domain.auth.exception.AuthErrorCode;
import com.comebackroom.domain.auth.repository.RefreshTokenRepository;
import com.comebackroom.domain.member.entity.Member;
import com.comebackroom.domain.member.repository.MemberRepository;
import com.comebackroom.global.exception.BusinessException;
import com.comebackroom.global.security.jwt.JwtTokenProvider;
import com.comebackroom.global.security.refresh.RefreshTokenGenerator;
import com.comebackroom.global.security.refresh.RefreshTokenHasher;
import com.comebackroom.global.security.refresh.RefreshTokenProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

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
@DisplayName("인증 토큰 서비스")
class AuthServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final String LOGIN_ID = "zin123";
    private static final String RAW_PASSWORD = "password123!";
    private static final String ENCODED_PASSWORD = "$2a$10$encoded-password";
    private static final String ACCESS_TOKEN = "access-token";
    private static final String OLD_REFRESH_TOKEN = "old-refresh-token";
    private static final String OLD_REFRESH_TOKEN_HASH = "a".repeat(64);
    private static final String NEW_REFRESH_TOKEN = "new-refresh-token";
    private static final String NEW_REFRESH_TOKEN_HASH = "b".repeat(64);
    private static final Instant NOW = Instant.parse("2026-11-06T03:00:00Z");
    private static final Duration REFRESH_TOKEN_EXPIRATION = Duration.ofDays(14);

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenGenerator refreshTokenGenerator;

    @Mock
    private RefreshTokenHasher refreshTokenHasher;

    private AuthService authService;
    private Member member;

    @BeforeEach
    void setUp() {
        RefreshTokenProperties refreshTokenProperties = new RefreshTokenProperties(
                REFRESH_TOKEN_EXPIRATION,
                "refreshToken",
                "/api/v1/auth",
                false,
                "Lax"
        );
        authService = new AuthService(
                memberRepository,
                refreshTokenRepository,
                passwordEncoder,
                jwtTokenProvider,
                refreshTokenGenerator,
                refreshTokenHasher,
                refreshTokenProperties,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        member = Member.createLocalMember(LOGIN_ID, ENCODED_PASSWORD, "zin@example.com");
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
    }

    @Test
    @DisplayName("아이디와 비밀번호가 일치하면 두 토큰을 발급하고 Refresh Token 해시를 저장한다")
    void login() {
        given(memberRepository.findByLoginId(LOGIN_ID)).willReturn(Optional.of(member));
        given(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).willReturn(true);
        given(jwtTokenProvider.createAccessToken(MEMBER_ID)).willReturn(ACCESS_TOKEN);
        given(refreshTokenGenerator.generateToken()).willReturn(NEW_REFRESH_TOKEN);
        given(refreshTokenHasher.hashToken(NEW_REFRESH_TOKEN)).willReturn(NEW_REFRESH_TOKEN_HASH);

        IssuedTokenResult result = authService.login(new LoginRequest(LOGIN_ID, RAW_PASSWORD));

        ArgumentCaptor<RefreshToken> refreshTokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(refreshTokenCaptor.capture());
        RefreshToken savedRefreshToken = refreshTokenCaptor.getValue();

        assertAll(
                () -> assertThat(result.accessToken()).isEqualTo(ACCESS_TOKEN),
                () -> assertThat(result.refreshToken()).isEqualTo(NEW_REFRESH_TOKEN),
                () -> assertThat(savedRefreshToken.getMember()).isSameAs(member),
                () -> assertThat(savedRefreshToken.getTokenHash())
                        .isEqualTo(NEW_REFRESH_TOKEN_HASH),
                () -> assertThat(savedRefreshToken.getTokenHash())
                        .doesNotContain(NEW_REFRESH_TOKEN),
                () -> assertThat(savedRefreshToken.getExpiresAt())
                        .isEqualTo(LocalDateTime.of(2026, 11, 20, 12, 0))
        );
    }

    @Test
    @DisplayName("유효한 Refresh Token을 새 Access Token과 Refresh Token으로 교체한다")
    void refresh() {
        RefreshToken savedRefreshToken = RefreshToken.create(
                member,
                OLD_REFRESH_TOKEN_HASH,
                LocalDateTime.of(2026, 11, 7, 12, 0)
        );
        given(refreshTokenHasher.hashToken(OLD_REFRESH_TOKEN))
                .willReturn(OLD_REFRESH_TOKEN_HASH);
        given(refreshTokenRepository.findByTokenHashForUpdate(OLD_REFRESH_TOKEN_HASH))
                .willReturn(Optional.of(savedRefreshToken));
        given(jwtTokenProvider.createAccessToken(MEMBER_ID)).willReturn(ACCESS_TOKEN);
        given(refreshTokenGenerator.generateToken()).willReturn(NEW_REFRESH_TOKEN);
        given(refreshTokenHasher.hashToken(NEW_REFRESH_TOKEN)).willReturn(NEW_REFRESH_TOKEN_HASH);

        IssuedTokenResult result = authService.refresh(OLD_REFRESH_TOKEN);

        ArgumentCaptor<RefreshToken> refreshTokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(refreshTokenCaptor.capture());
        RefreshToken rotatedRefreshToken = refreshTokenCaptor.getValue();

        var repositoryCallOrder = inOrder(refreshTokenRepository);
        repositoryCallOrder.verify(refreshTokenRepository).delete(savedRefreshToken);
        repositoryCallOrder.verify(refreshTokenRepository).flush();
        repositoryCallOrder.verify(refreshTokenRepository).save(rotatedRefreshToken);

        assertAll(
                () -> assertThat(result.accessToken()).isEqualTo(ACCESS_TOKEN),
                () -> assertThat(result.refreshToken()).isEqualTo(NEW_REFRESH_TOKEN),
                () -> assertThat(rotatedRefreshToken.getMember()).isSameAs(member),
                () -> assertThat(rotatedRefreshToken.getTokenHash())
                        .isEqualTo(NEW_REFRESH_TOKEN_HASH),
                () -> assertThat(rotatedRefreshToken.getExpiresAt())
                        .isEqualTo(LocalDateTime.of(2026, 11, 20, 12, 0))
        );
    }

    @Test
    @DisplayName("Refresh Token 쿠키가 없으면 인증 실패로 처리한다")
    void failToRefreshWithoutRefreshToken() {
        assertInvalidRefreshToken(() -> authService.refresh(null));

        verify(refreshTokenHasher, never()).hashToken(org.mockito.ArgumentMatchers.any());
        verify(refreshTokenRepository, never())
                .findByTokenHashForUpdate(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("저장되지 않은 Refresh Token은 인증 실패로 처리한다")
    void failToRefreshWithUnknownRefreshToken() {
        given(refreshTokenHasher.hashToken(OLD_REFRESH_TOKEN))
                .willReturn(OLD_REFRESH_TOKEN_HASH);
        given(refreshTokenRepository.findByTokenHashForUpdate(OLD_REFRESH_TOKEN_HASH))
                .willReturn(Optional.empty());

        assertInvalidRefreshToken(() -> authService.refresh(OLD_REFRESH_TOKEN));

        verify(refreshTokenRepository, never()).delete(org.mockito.ArgumentMatchers.any());
        verify(refreshTokenRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("만료시각과 현재시각이 같으면 만료된 Refresh Token으로 처리한다")
    void failToRefreshWithExpiredRefreshToken() {
        RefreshToken expiredRefreshToken = RefreshToken.create(
                member,
                OLD_REFRESH_TOKEN_HASH,
                LocalDateTime.of(2026, 11, 6, 12, 0)
        );
        given(refreshTokenHasher.hashToken(OLD_REFRESH_TOKEN))
                .willReturn(OLD_REFRESH_TOKEN_HASH);
        given(refreshTokenRepository.findByTokenHashForUpdate(OLD_REFRESH_TOKEN_HASH))
                .willReturn(Optional.of(expiredRefreshToken));

        assertInvalidRefreshToken(() -> authService.refresh(OLD_REFRESH_TOKEN));

        verify(refreshTokenRepository, never()).delete(org.mockito.ArgumentMatchers.any());
        verify(refreshTokenRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("존재하지 않는 아이디는 동일한 인증 실패 예외를 반환한다")
    void failToLoginWithUnknownLoginId() {
        given(memberRepository.findByLoginId(LOGIN_ID)).willReturn(Optional.empty());

        assertInvalidCredentials(() -> authService.login(new LoginRequest(LOGIN_ID, RAW_PASSWORD)));

        verify(refreshTokenRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("비밀번호가 일치하지 않으면 동일한 인증 실패 예외를 반환한다")
    void failToLoginWithWrongPassword() {
        given(memberRepository.findByLoginId(LOGIN_ID)).willReturn(Optional.of(member));
        given(passwordEncoder.matches("wrong-password", ENCODED_PASSWORD)).willReturn(false);

        assertInvalidCredentials(
                () -> authService.login(new LoginRequest(LOGIN_ID, "wrong-password"))
        );

        verify(refreshTokenRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("아이디 또는 비밀번호가 비어 있으면 인증 실패로 처리한다")
    void failToLoginWithBlankCredential() {
        assertAll(
                () -> assertInvalidCredentials(
                        () -> authService.login(new LoginRequest(" ", RAW_PASSWORD))
                ),
                () -> assertInvalidCredentials(
                        () -> authService.login(new LoginRequest(LOGIN_ID, null))
                )
        );

        verify(memberRepository, never()).findByLoginId(org.mockito.ArgumentMatchers.any());
        verify(refreshTokenRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private static void assertInvalidCredentials(Runnable loginAction) {
        assertThatThrownBy(loginAction::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(AuthErrorCode.INVALID_CREDENTIALS));
    }

    private static void assertInvalidRefreshToken(Runnable refreshAction) {
        assertThatThrownBy(refreshAction::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN));
    }
}
