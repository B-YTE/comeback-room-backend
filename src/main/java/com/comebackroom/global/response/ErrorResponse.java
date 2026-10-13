package com.comebackroom.global.response;

import com.comebackroom.global.exception.ErrorCode;

import java.time.LocalDateTime;

public record ErrorResponse(
        int status,
        String code,
        String message,
        String path,
        LocalDateTime timestamp
) {
    public static ErrorResponse of(ErrorCode errorCode, String path) {
        return new ErrorResponse(
                errorCode.getHttpStatus().value(),
                errorCode.getCode(),
                errorCode.getMessage(),
                path,
                LocalDateTime.now()
        );
    }
}
