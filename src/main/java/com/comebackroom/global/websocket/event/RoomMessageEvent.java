package com.comebackroom.global.websocket.event;

import com.comebackroom.global.websocket.message.RoomMessageType;

import java.util.Objects;

public record RoomMessageEvent(
        RoomMessageType type,
        Long roomId,
        Object payload
) {
    public RoomMessageEvent {
        Objects.requireNonNull(type, "type은 필수입니다.");
        Objects.requireNonNull(roomId, "roomId는 필수입니다.");
    }
}
