package com.comebackroom.domain.auth.controller;

import com.comebackroom.domain.auth.dto.LoginTokenResult;
import com.comebackroom.domain.auth.dto.request.LoginRequest;
import com.comebackroom.domain.auth.exception.AuthErrorCode;
import com.comebackroom.domain.auth.service.AuthService;
import com.comebackroom.global.exception.BusinessException;
import com.comebackroom.global.exception.GlobalExceptionHandler;
import com.comebackroom.global.security.refresh.RefreshTokenCookieProvider;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(controllers = AuthController.class)
@DisplayName("자체 로그인 API")
class AuthControllerTest {

    private static final String ACCESS_TOKEN = "access-token";
    private static final String REFRESH_TOKEN = "refresh-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private RefreshTokenCookieProvider refreshTokenCookieProvider;

    @Test
    @DisplayName("로그인 성공 시 Access Token 응답과 Refresh Token 쿠키를 반환한다")
    void login() throws Exception {
        LoginRequest loginRequest = new LoginRequest("zin123", "password123!");
        ResponseCookie refreshTokenCookie = ResponseCookie.from("refreshToken", REFRESH_TOKEN)
                .httpOnly(true)
                .secure(false)
                .path("/api/v1/auth")
                .sameSite("Lax")
                .maxAge(Duration.ofDays(14))
                .build();
        given(authService.login(loginRequest))
                .willReturn(new LoginTokenResult(ACCESS_TOKEN, REFRESH_TOKEN));
        given(refreshTokenCookieProvider.createCookie(REFRESH_TOKEN))
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
                .andExpect(header().string(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString()))
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("LOGIN_SUCCESS"))
                .andExpect(jsonPath("$.message").value("로그인에 성공했습니다."))
                .andExpect(jsonPath("$.data.accessToken").value(ACCESS_TOKEN))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist());
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
}
