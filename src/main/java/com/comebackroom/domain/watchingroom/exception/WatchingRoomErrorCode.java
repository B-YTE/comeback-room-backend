package com.comebackroom.domain.watchingroom.exception;

import com.comebackroom.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum WatchingRoomErrorCode implements ErrorCode {
    ROOM_NOT_DELETABLE(HttpStatus.CONFLICT, "시작 전인 방만 삭제할 수 있습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
