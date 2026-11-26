package com.comebackroom.domain.auth.exception;

import com.comebackroom.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    DUPLICATE_MEMBER(HttpStatus.CONFLICT, "이미 사용 중인 회원 정보입니다."),
    INVALID_CREDENTIALS(
            HttpStatus.UNAUTHORIZED,
            "아이디 또는 비밀번호가 일치하지 않습니다."
    ),
    INVALID_REFRESH_TOKEN(
            HttpStatus.UNAUTHORIZED,
            "Refresh Token이 만료되었거나 유효하지 않습니다."
    ),
    DUPLICATE_EMAIL(
            HttpStatus.CONFLICT,
            "이미 가입된 이메일입니다."
    ),
    EMAIL_RESEND_TOO_SOON(
            HttpStatus.TOO_MANY_REQUESTS,
            "인증코드 재요청 대기시간이 지나지 않았습니다."
    ),
    EMAIL_REQUEST_LIMIT_EXCEEDED(
            HttpStatus.TOO_MANY_REQUESTS,
            "인증코드 요청 횟수를 초과했습니다."
    ),
    INVALID_VERIFICATION_CODE(
            HttpStatus.BAD_REQUEST,
            "인증코드가 일치하지 않거나 만료되었습니다."
    );

    private final HttpStatus httpStatus;
    private final String message;
}
