package com.comebackroom.global.exception;

import com.comebackroom.global.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Business 예외
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException e, HttpServletRequest request
    ) {
        log.warn("비즈니스 예외 code={} path={}", e.getErrorCode().getCode(), request.getRequestURI());
        return toResponse(e.getErrorCode(), request);
    }

    // @Valid 검증 실패 예외 (400)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException e, HttpServletRequest request
    ) {
        String fields = e.getBindingResult().getFieldErrors().stream()
                        .map(fieldError -> fieldError.getField() + "("
                            + fieldError.getDefaultMessage() + ")")
                        .collect(Collectors.joining(", "));
        log.warn("검증 실패 path={} fields=[{}]", request.getRequestURI(), fields);
        return toResponse(GlobalErrorCode.INVALID_INPUT_VALUE, request);
    }

    // HandlerMethodValidationException : @RequestParam, @PathVariable 등에 직접 건 검증 실패
    // ConstraintViolationException     : 클래스에 @Validated를 붙인 경우의 검증 실패
    // HttpMessageNotReadableException  : 깨진 JSON, 날짜 형식 오류, 없는 enum 값, body 누락
    // MethodArgumentTypeMismatchException : /rooms/abc 처럼 숫자 자리에 문자가 온 경우
    // MissingServletRequestParameterException : 필수 쿼리 파라미터 누락
    // (400)
    @ExceptionHandler({
            HandlerMethodValidationException.class,
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(
            Exception e, HttpServletRequest request
    ) {
        log.warn("잘못된 요청 path={} type={}", request.getRequestURI(), e.getClass().getSimpleName());
        return toResponse(GlobalErrorCode.INVALID_INPUT_VALUE, request);
    }

    // 존재하지 않는 URL (404)
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            NoResourceFoundException e, HttpServletRequest request
    ) {
        log.warn("존재하지 않는 경로 path={}", request.getRequestURI());
        return toResponse(GlobalErrorCode.API_NOT_FOUND, request);
    }

    // 지원하지 않는 HTTP 메서드 (405)
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException e, HttpServletRequest request
    ) {
        log.warn("지원하지 않는 메서드 path={} method={}", request.getRequestURI(), e.getMethod());
        return toResponse(GlobalErrorCode.METHOD_NOT_ALLOWED, request);
    }

    // 예상치 못한 예외 (500)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception e, HttpServletRequest request
    ) {
        log.error("예상치 못한 예외 path={}", request.getRequestURI(), e);
        return toResponse(GlobalErrorCode.INTERNAL_SERVER_ERROR, request);
    }


    // ErrorResponse 생성용 헬퍼 메소드
    private ResponseEntity<ErrorResponse> toResponse(ErrorCode errorCode, HttpServletRequest request) {
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ErrorResponse.of(errorCode, request.getRequestURI()));
    }
}
