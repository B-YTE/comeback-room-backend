package com.comebackroom.domain.auth.controller;

import com.comebackroom.domain.auth.dto.EmailVerificationTokenResult;
import com.comebackroom.domain.auth.dto.IssuedTokenResult;
import com.comebackroom.domain.auth.dto.request.LoginRequest;
import com.comebackroom.domain.auth.dto.request.SendEmailVerificationCodeRequest;
import com.comebackroom.domain.auth.dto.request.VerifyEmailVerificationCodeRequest;
import com.comebackroom.domain.auth.enums.EmailVerificationPurpose;
import com.comebackroom.domain.auth.exception.AuthErrorCode;
import com.comebackroom.domain.auth.service.AuthService;
import com.comebackroom.domain.auth.service.EmailVerificationService;
import com.comebackroom.global.exception.BusinessException;
import com.comebackroom.global.exception.GlobalExceptionHandler;
import com.comebackroom.global.security.refresh.RefreshTokenCookieProvider;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(controllers = AuthController.class)
@DisplayName("인증 API")
class AuthControllerTest {

    private static final String ACCESS_TOKEN = "access-token";
    private static final String OLD_REFRESH_TOKEN = "old-refresh-token";
    private static final String NEW_REFRESH_TOKEN = "new-refresh-token";
    private static final String VERIFICATION_TOKEN = "email-verification-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private EmailVerificationService emailVerificationService;

    @MockitoBean
    private RefreshTokenCookieProvider refreshTokenCookieProvider;

    @Test
    @DisplayName("로그인 성공 시 Access Token 응답과 Refresh Token 쿠키를 반환한다")
    void login() throws Exception {
        LoginRequest loginRequest = new LoginRequest("zin123", "password123!");
        ResponseCookie refreshTokenCookie = ResponseCookie.from("refreshToken", NEW_REFRESH_TOKEN)
                .httpOnly(true)
                .secure(false)
                .path("/api/v1/auth")
                .sameSite("Lax")
                .maxAge(Duration.ofDays(14))
                .build();
        given(authService.login(loginRequest))
                .willReturn(new IssuedTokenResult(ACCESS_TOKEN, NEW_REFRESH_TOKEN));
        given(refreshTokenCookieProvider.createCookie(NEW_REFRESH_TOKEN))
                .willReturn(refreshTokenCookie);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "zin123",
                                  "password": "password123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(cookie().value("refreshToken", NEW_REFRESH_TOKEN))
                .andExpect(cookie().path("refreshToken", "/api/v1/auth"))
                .andExpect(cookie().maxAge("refreshToken", (int) Duration.ofDays(14).toSeconds()))
                .andExpect(cookie().httpOnly("refreshToken", true))
                .andExpect(cookie().secure("refreshToken", false))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Expires=")))
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("LOGIN_SUCCESS"))
                .andExpect(jsonPath("$.message").value("로그인에 성공했습니다."))
                .andExpect(jsonPath("$.data.accessToken").value(ACCESS_TOKEN))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist());
    }

    @Test
    @DisplayName("재발급 성공 시 새 Access Token 응답과 교체된 Refresh Token 쿠키를 반환한다")
    void refresh() throws Exception {
        ResponseCookie refreshTokenCookie = ResponseCookie.from(
                        "refreshToken",
                        NEW_REFRESH_TOKEN
                )
                .httpOnly(true)
                .secure(false)
                .path("/api/v1/auth")
                .sameSite("Lax")
                .maxAge(Duration.ofDays(14))
                .build();
        given(authService.refresh(OLD_REFRESH_TOKEN))
                .willReturn(new IssuedTokenResult(ACCESS_TOKEN, NEW_REFRESH_TOKEN));
        given(refreshTokenCookieProvider.createCookie(NEW_REFRESH_TOKEN))
                .willReturn(refreshTokenCookie);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refreshToken", OLD_REFRESH_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(cookie().value("refreshToken", NEW_REFRESH_TOKEN))
                .andExpect(cookie().path("refreshToken", "/api/v1/auth"))
                .andExpect(cookie().maxAge("refreshToken", (int) Duration.ofDays(14).toSeconds()))
                .andExpect(cookie().httpOnly("refreshToken", true))
                .andExpect(cookie().secure("refreshToken", false))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Expires=")))
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("TOKEN_REFRESH_SUCCESS"))
                .andExpect(jsonPath("$.message").value("토큰이 재발급되었습니다."))
                .andExpect(jsonPath("$.data.accessToken").value(ACCESS_TOKEN))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist());
    }

    @Test
    @DisplayName("이메일 인증코드 발급 성공 시 데이터 없는 공통 성공 응답을 반환한다")
    void sendEmailVerificationCode() throws Exception {
        mockMvc.perform(post("/api/v1/auth/email/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "zin@example.com",
                                  "purpose": "SIGN_UP"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("EMAIL_CODE_SENT"))
                .andExpect(jsonPath("$.message")
                        .value("인증코드가 이메일로 발송되었습니다."))
                .andExpect(jsonPath("$.data").value(nullValue()));

        verify(emailVerificationService).sendCode(
                new SendEmailVerificationCodeRequest(
                        "zin@example.com",
                        EmailVerificationPurpose.SIGN_UP
                )
        );
    }

    @Test
    @DisplayName("이메일 인증 성공 시 verificationToken을 반환한다")
    void verifyEmailVerificationCode() throws Exception {
        given(emailVerificationService.verifyCode(any(
                VerifyEmailVerificationCodeRequest.class
        ))).willReturn(new EmailVerificationTokenResult(VERIFICATION_TOKEN));

        mockMvc.perform(post("/api/v1/auth/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "zin@example.com",
                                  "verificationCode": "123456",
                                  "purpose": "SIGN_UP"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFIED"))
                .andExpect(jsonPath("$.message").value("이메일 인증이 완료되었습니다."))
                .andExpect(jsonPath("$.data.verificationToken").value(VERIFICATION_TOKEN));
    }

    @Test
    @DisplayName("가입된 이메일의 회원가입 인증 요청은 공통 409 오류 응답을 반환한다")
    void failToSendSignUpCodeToRegisteredEmail() throws Exception {
        willThrow(new BusinessException(AuthErrorCode.DUPLICATE_EMAIL))
                .given(emailVerificationService)
                .sendCode(any(SendEmailVerificationCodeRequest.class));

        mockMvc.perform(post("/api/v1/auth/email/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "zin@example.com",
                                  "purpose": "SIGN_UP"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"))
                .andExpect(jsonPath("$.message").value("이미 가입된 이메일입니다."))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/email/send"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("인증코드 요청 제한을 초과하면 공통 429 오류 응답을 반환한다")
    void failToSendEmailVerificationCodeOverLimit() throws Exception {
        willThrow(new BusinessException(AuthErrorCode.EMAIL_REQUEST_LIMIT_EXCEEDED))
                .given(emailVerificationService)
                .sendCode(any(SendEmailVerificationCodeRequest.class));

        mockMvc.perform(post("/api/v1/auth/email/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "zin@example.com",
                                  "purpose": "SIGN_UP"
                                }
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.code").value("EMAIL_REQUEST_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.message")
                        .value("인증코드 요청 횟수를 초과했습니다."))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/email/send"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("인증코드가 잘못되었거나 만료되면 공통 400 오류 응답을 반환한다")
    void failToVerifyInvalidEmailVerificationCode() throws Exception {
        given(emailVerificationService.verifyCode(any(
                VerifyEmailVerificationCodeRequest.class
        ))).willThrow(new BusinessException(AuthErrorCode.INVALID_VERIFICATION_CODE));

        mockMvc.perform(post("/api/v1/auth/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "zin@example.com",
                                  "verificationCode": "999999",
                                  "purpose": "SIGN_UP"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_CODE"))
                .andExpect(jsonPath("$.message")
                        .value("인증코드가 일치하지 않거나 만료되었습니다."))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/email/verify"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("이메일 형식이 잘못되거나 목적이 없으면 공통 400 오류 응답을 반환한다")
    void failToSendEmailVerificationCodeWithInvalidInput() throws Exception {
        mockMvc.perform(post("/api/v1/auth/email/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "invalid-email",
                                  "purpose": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));

        verify(emailVerificationService, never()).sendCode(any());
    }

    @Test
    @DisplayName("Refresh Token 쿠키가 없으면 공통 401 오류 응답을 반환한다")
    void failToRefreshWithoutRefreshTokenCookie() throws Exception {
        given(authService.refresh(null))
                .willThrow(new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"))
                .andExpect(jsonPath("$.message")
                        .value("Refresh Token이 만료되었거나 유효하지 않습니다."))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/refresh"))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(refreshTokenCookieProvider, never()).createCookie(any());
    }

    @Test
    @DisplayName("인증 정보가 일치하지 않으면 공통 401 오류 응답을 반환한다")
    void failToLoginWithInvalidCredentials() throws Exception {
        given(authService.login(any(LoginRequest.class)))
                .willThrow(new BusinessException(AuthErrorCode.INVALID_CREDENTIALS));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "zin123",
                                  "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message")
                        .value("아이디 또는 비밀번호가 일치하지 않습니다."))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/login"))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(refreshTokenCookieProvider, never()).createCookie(any());
    }

    @Test
    @DisplayName("아이디 또는 비밀번호가 비어 있으면 공통 400 오류 응답을 반환한다")
    void failToLoginWithBlankCredentials() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": " ",
                                  "password": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.message").value("잘못된 입력값입니다."))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/login"))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(authService, never()).login(any(LoginRequest.class));
        verify(refreshTokenCookieProvider, never()).createCookie(any());
    }
}
