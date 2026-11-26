package com.comebackroom.domain.watchingroom.response;

import com.comebackroom.global.response.SuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum WatchingRoomSuccessCode implements SuccessCode {
    WATCHING_ROOM_JOINED(HttpStatus.OK, "시청방에 입장했습니다."),
    WATCHING_ROOM_LEFT(HttpStatus.OK, "시청방에서 나갔습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
