package com.comebackroom.domain.auth.controller;

import com.comebackroom.domain.auth.dto.IssuedTokenResult;
import com.comebackroom.domain.auth.dto.request.LoginRequest;
import com.comebackroom.domain.auth.dto.response.LoginResponse;
import com.comebackroom.domain.auth.dto.response.TokenRefreshResponse;
import com.comebackroom.domain.auth.response.AuthSuccessCode;
import com.comebackroom.domain.auth.service.AuthService;
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

    private void addRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                refreshTokenCookieProvider.createCookie(refreshToken).toString()
        );
    }
}
