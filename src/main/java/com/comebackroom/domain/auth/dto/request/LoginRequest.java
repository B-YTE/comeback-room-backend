package com.comebackroom.domain.auth.dto.request;

public record LoginRequest(
        String loginId,
        String password
) {
}
