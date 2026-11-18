package com.comebackroom.global.websocket.response;

import com.comebackroom.global.exception.ErrorCode;

import java.time.LocalDateTime;

public record StompErrorResponse(
        String code,
        String message,
        LocalDateTime timestamp
) {
    public static StompErrorResponse of(ErrorCode errorCode) {
        return new StompErrorResponse(
                errorCode.getCode(), errorCode.getMessage(), LocalDateTime.now()
        );
    }
}
