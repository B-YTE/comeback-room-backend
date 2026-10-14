package com.comebackroom.global.response;

import org.springframework.http.HttpStatus;

public interface SuccessCode {
    HttpStatus getHttpStatus();
    String getMessage();
    String name();

    default String getCode() {
        return name();
    }
}
