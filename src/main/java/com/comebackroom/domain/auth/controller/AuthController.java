package com.comebackroom.domain.auth.controller;

import com.comebackroom.domain.auth.dto.LoginTokenResult;
import com.comebackroom.domain.auth.dto.request.LoginRequest;
import com.comebackroom.domain.auth.dto.response.LoginResponse;
import com.comebackroom.domain.auth.response.AuthSuccessCode;
import com.comebackroom.domain.auth.service.AuthService;
import com.comebackroom.global.response.ApiResponse;
import com.comebackroom.global.security.refresh.RefreshTokenCookieProvider;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
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
            @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        LoginTokenResult loginTokenResult = authService.login(request);
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                refreshTokenCookieProvider.createCookie(loginTokenResult.refreshToken()).toString()
        );

        return ApiResponse.success(
                AuthSuccessCode.LOGIN_SUCCESS,
                new LoginResponse(loginTokenResult.accessToken())
        );
    }
}
