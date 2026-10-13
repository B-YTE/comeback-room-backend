package com.comebackroom.global.response;

import org.springframework.http.ResponseEntity;

public record ApiResponse<T>(
        int status,
        String code,
        String message,
        T data
) {

    // 응답 데이터를 포함한 성공 응답을 반환합니다.
    // 컨트롤러에서 성공 응답을 반환할 때 사용합니다.
    public static <T>ResponseEntity<ApiResponse<T>> success(SuccessCode successCode, T data) {
        return ResponseEntity.status(successCode.getHttpStatus())
                .body(of(successCode, data));
    }

    // 응답 데이터 없이 성공 응답을 반환합니다.
    // 응답 본문의 data 필드는 null로 설정됩니다.
    public static ResponseEntity<ApiResponse<Void>> success(SuccessCode successCode) {
        return ResponseEntity.status(successCode.getHttpStatus())
                .body(of(successCode, null));
    }

    // 성공 응답 객체 생성 헬퍼 메소드
    private static <T> ApiResponse<T> of(SuccessCode successCode, T data) {
        return new ApiResponse<>(
                successCode.getHttpStatus().value(),
                successCode.getCode(),
                successCode.getMessage(),
                data
        );
    }
}
