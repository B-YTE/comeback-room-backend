package com.comebackroom.domain.poll.response;

import com.comebackroom.global.response.SuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PollSuccessCode implements SuccessCode {

    POLL_CREATED(HttpStatus.CREATED, "투표가 성공적으로 생성되었습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
