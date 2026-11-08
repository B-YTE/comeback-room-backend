package com.comebackroom.domain.auth.dto;

public record IssuedTokenResult(
        String accessToken,
        String refreshToken
) {

    public IssuedTokenResult {
        accessToken = requireToken(accessToken, "Access Token");
        refreshToken = requireToken(refreshToken, "Refresh Token");
    }

    @Override
    public String toString() {
        return "IssuedTokenResult[accessToken=***, refreshToken=***]";
    }

    private static String requireToken(String token, String tokenName) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException(tokenName + " 값은 필수입니다.");
        }
        return token;
    }
}
