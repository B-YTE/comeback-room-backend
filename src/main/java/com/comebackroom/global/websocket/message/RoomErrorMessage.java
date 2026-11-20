package com.comebackroom.global.websocket.message;

import com.comebackroom.global.exception.ErrorCode;

import java.time.LocalDateTime;

public record RoomErrorMessage(
        String code,
        String message,
        String destination, // 실패한 요청이 보낸 주소
        LocalDateTime timestamp
) {
    public static RoomErrorMessage of(ErrorCode errorCode, String destination) {
        return new RoomErrorMessage(
                errorCode.getCode(), errorCode.getMessage(), destination, LocalDateTime.now()
        );
    }
}
