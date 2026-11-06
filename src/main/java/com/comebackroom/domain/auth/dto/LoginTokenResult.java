package com.comebackroom.domain.auth.dto;

public final class LoginTokenResult {

    private final String accessToken;
    private final String refreshToken;

    public LoginTokenResult(String accessToken, String refreshToken) {
        this.accessToken = requireToken(accessToken, "Access Token");
        this.refreshToken = requireToken(refreshToken, "Refresh Token");
    }

    public String accessToken() {
        return accessToken;
    }

    public String refreshToken() {
        return refreshToken;
    }

    private static String requireToken(String token, String tokenName) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException(tokenName + " 값은 필수입니다.");
        }
        return token;
    }
}
