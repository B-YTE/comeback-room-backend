package com.comebackroom.global.websocket.handler;

import com.comebackroom.global.exception.BusinessException;
import com.comebackroom.global.exception.ErrorCode;
import com.comebackroom.global.exception.GlobalErrorCode;
import com.comebackroom.global.websocket.response.StompErrorResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompErrorHandler extends StompSubProtocolErrorHandler {

    private final ObjectMapper objectMapper;

    @Override
    protected Message<byte[]> handleInternal(
            StompHeaderAccessor errorHeaderAccessor, // 내보낼 ERROR 프레임
            byte[] errorPayload,
            Throwable cause,
            StompHeaderAccessor clientHeaderAccessor // 실패를 일으킨 클라이언트 프레임
    ) {
        if (cause == null) { // 프레임 처리 실패가 아니라 서버(브로커)가 만든 ERROR는 가공하지 않고 그대로 전달
            return super.handleInternal(errorHeaderAccessor, errorPayload, null, clientHeaderAccessor);
        }

        ErrorCode errorCode = resolveErrorCode(cause);
        errorHeaderAccessor.setMessage(errorCode.getCode()); // ERROR 프레임의 message 헤더에 code 담기
        errorHeaderAccessor.setContentType(MimeTypeUtils.APPLICATION_JSON); // 본문 JSON

        byte[] payload = objectMapper.writeValueAsBytes(StompErrorResponse.of(errorCode));
        return super.handleInternal(errorHeaderAccessor, payload, cause, clientHeaderAccessor);
    }

    // 인터셉터가 던진 BusinessException은 채널을 거치며 MessageDeliveryException에 감싸지므로
    // cause를 따라 내려가며 BusinessException을 찾음
    private ErrorCode resolveErrorCode(Throwable cause) {
        for (Throwable t = cause; t != null; t = t.getCause()) {
            if (t instanceof BusinessException businessException) {
                ErrorCode errorCode = businessException.getErrorCode();
                log.warn("STOMP 예외 code={}", errorCode.getCode());
                return errorCode;
            }
        }
        log.error("STOMP 처리 중 예상치 못한 예외", cause);
        return GlobalErrorCode.INTERNAL_SERVER_ERROR;
    }
}
