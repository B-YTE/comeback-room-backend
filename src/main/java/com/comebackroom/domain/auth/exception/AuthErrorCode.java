package com.comebackroom.domain.auth.exception;

import com.comebackroom.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    INVALID_CREDENTIALS(
            HttpStatus.UNAUTHORIZED,
            "아이디 또는 비밀번호가 일치하지 않습니다."
    ),
    INVALID_REFRESH_TOKEN(
            HttpStatus.UNAUTHORIZED,
            "Refresh Token이 만료되었거나 유효하지 않습니다."
    );

    private final HttpStatus httpStatus;
    private final String message;
}
