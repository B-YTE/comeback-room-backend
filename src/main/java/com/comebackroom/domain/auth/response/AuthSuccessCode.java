package com.comebackroom.domain.auth.response;

import com.comebackroom.global.response.SuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthSuccessCode implements SuccessCode {

    LOGIN_SUCCESS(HttpStatus.OK, "로그인에 성공했습니다."),
    TOKEN_REFRESH_SUCCESS(HttpStatus.OK, "토큰이 재발급되었습니다."),
    EMAIL_CODE_SENT(HttpStatus.OK, "인증코드가 이메일로 발송되었습니다."),
    EMAIL_VERIFIED(HttpStatus.OK, "이메일 인증이 완료되었습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
