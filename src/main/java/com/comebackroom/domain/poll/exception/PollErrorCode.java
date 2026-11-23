package com.comebackroom.domain.poll.exception;

import com.comebackroom.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PollErrorCode implements ErrorCode {

    POLL_CREATE_FORBIDDEN(HttpStatus.FORBIDDEN, "방장만 투표를 생성할 수 있습니다."),
    WATCHING_ROOM_ALREADY_ENDED(HttpStatus.CONFLICT, "이미 종료된 시청방입니다."),
    NOT_WATCHING_ROOM_PARTICIPANT(HttpStatus.FORBIDDEN, "비공개 시청방은 참여자만 조회할 수 있습니다."),
    POLL_DELETE_FORBIDDEN(HttpStatus.FORBIDDEN, "방장만 투표를 삭제할 수 있습니다."),
    POLL_ALREADY_ENDED(HttpStatus.CONFLICT, "이미 마감된 투표입니다."),
    POLL_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 투표입니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
