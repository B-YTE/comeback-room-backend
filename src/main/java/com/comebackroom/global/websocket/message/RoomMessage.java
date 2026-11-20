package com.comebackroom.global.websocket.message;

import java.time.LocalDateTime;
import java.util.Objects;

public record RoomMessage(
        RoomMessageType type,
        Long roomId,
        LocalDateTime serverTime,
        Object payload
) {
    public RoomMessage {
        Objects.requireNonNull(type, "type은 필수입니다.");
        Objects.requireNonNull(roomId, "roomId는 필수입니다.");
        Objects.requireNonNull(serverTime, "serverTime은 필수입니다.");
    }

    public static RoomMessage of(RoomMessageType type, Long roomId, Object payload) {
        return new RoomMessage(type, roomId, LocalDateTime.now(), payload);
    }
}
