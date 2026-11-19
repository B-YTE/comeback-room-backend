package com.comebackroom.domain.auth.controller;

import com.comebackroom.domain.auth.dto.EmailVerificationTokenResult;
import com.comebackroom.domain.auth.dto.IssuedTokenResult;
import com.comebackroom.domain.auth.dto.request.LoginRequest;
import com.comebackroom.domain.auth.dto.request.SendEmailVerificationCodeRequest;
import com.comebackroom.domain.auth.dto.request.VerifyEmailVerificationCodeRequest;
import com.comebackroom.domain.auth.dto.response.EmailVerificationResponse;
import com.comebackroom.domain.auth.dto.response.LoginResponse;
import com.comebackroom.domain.auth.dto.response.TokenRefreshResponse;
import com.comebackroom.domain.auth.response.AuthSuccessCode;
import com.comebackroom.domain.auth.service.AuthService;
import com.comebackroom.domain.auth.service.EmailVerificationService;
import com.comebackroom.global.response.ApiResponse;
import com.comebackroom.global.security.refresh.RefreshTokenCookieProvider;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final RefreshTokenCookieProvider refreshTokenCookieProvider;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        IssuedTokenResult issuedTokenResult = authService.login(request);
        addRefreshTokenCookie(response, issuedTokenResult.refreshToken());

        return ApiResponse.success(
                AuthSuccessCode.LOGIN_SUCCESS,
                new LoginResponse(issuedTokenResult.accessToken())
        );
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> refresh(
            @CookieValue(
                    name = "${app.refresh-token.cookie-name}",
                    required = false
            ) String refreshToken,
            HttpServletResponse response
    ) {
        IssuedTokenResult issuedTokenResult = authService.refresh(refreshToken);
        addRefreshTokenCookie(response, issuedTokenResult.refreshToken());

        return ApiResponse.success(
                AuthSuccessCode.TOKEN_REFRESH_SUCCESS,
                new TokenRefreshResponse(issuedTokenResult.accessToken())
        );
    }

    @PostMapping("/email/send")
    public ResponseEntity<ApiResponse<Void>> sendEmailVerificationCode(
            @Valid @RequestBody SendEmailVerificationCodeRequest request
    ) {
        emailVerificationService.sendCode(request);
        return ApiResponse.success(AuthSuccessCode.EMAIL_CODE_SENT);
    }

    @PostMapping("/email/verify")
    public ResponseEntity<ApiResponse<EmailVerificationResponse>> verifyEmailVerificationCode(
            @Valid @RequestBody VerifyEmailVerificationCodeRequest request
    ) {
        EmailVerificationTokenResult result = emailVerificationService.verifyCode(request);
        return ApiResponse.success(
                AuthSuccessCode.EMAIL_VERIFIED,
                new EmailVerificationResponse(result.verificationToken())
        );
    }

    private void addRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                refreshTokenCookieProvider.createCookie(refreshToken).toString()
        );
    }
}
