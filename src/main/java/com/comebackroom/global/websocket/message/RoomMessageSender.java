package com.comebackroom.global.websocket.message;

import com.comebackroom.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class RoomMessageSender {

    private static final String USER_ERROR_DESTINATION = "/sub/errors";

    private final SimpMessageSendingOperations messagingTemplate;

    // 방 구독자 전원에게 전송
    public void sendToRoom(RoomMessageType type, Long roomId, Object payload) {
        RoomMessage message = RoomMessage.of(type, roomId, payload);
        messagingTemplate.convertAndSend(type.destination(roomId), message);
    }

    // 회원 한 명에게만 오류 전송
    public void sendError(Long memberId, ErrorCode errorCode, String destination) {
        Objects.requireNonNull(memberId, "memberId는 필수입니다.");
        RoomErrorMessage message = RoomErrorMessage.of(errorCode, destination);
        messagingTemplate.convertAndSendToUser(String.valueOf(memberId), USER_ERROR_DESTINATION, message);
    }
}
