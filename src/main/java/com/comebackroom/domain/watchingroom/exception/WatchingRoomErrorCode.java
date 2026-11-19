package com.comebackroom.domain.watchingroom.exception;

import com.comebackroom.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum WatchingRoomErrorCode implements ErrorCode {
    WATCHING_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 시청방입니다."),
    ROOM_NOT_DELETABLE(HttpStatus.CONFLICT, "시작 전인 방만 삭제할 수 있습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
