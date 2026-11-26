package com.comebackroom.domain.watchingroom.exception;

import com.comebackroom.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum WatchingRoomErrorCode implements ErrorCode {
    WATCHING_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 시청방입니다."),
    WATCHING_ROOM_FULL(HttpStatus.CONFLICT, "시청방 정원이 가득 찼습니다."),
    WATCHING_ROOM_NOT_JOINABLE(HttpStatus.CONFLICT, "지금은 입장할 수 없는 시청방입니다."),

    INVALID_PRIVATE_CODE(HttpStatus.FORBIDDEN, "입장 코드가 올바르지 않습니다."),
    PRIVATE_CODE_REQUIRED(HttpStatus.FORBIDDEN, "입장 코드가 필요한 시청방입니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
