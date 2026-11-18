package com.comebackroom.global.websocket.exception;

import com.comebackroom.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum WebSocketErrorCode implements ErrorCode {

    INVALID_TICKET(HttpStatus.UNAUTHORIZED,
            "유효하지 않은 ticket입니다. 참가 API로 새 ticket을 받아 다시 연결해 주세요."),
    NOT_ROOM_PARTICIPANT(
            HttpStatus.FORBIDDEN,
            "연결한 시청방만 구독할 수 있습니다."
    ),
    INVALID_DESTINATION(
            HttpStatus.BAD_REQUEST,
            "허용되지 않는 destination입니다."
    );

    private final HttpStatus httpStatus;
    private final String message;
}
