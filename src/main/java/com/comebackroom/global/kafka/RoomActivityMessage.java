package com.comebackroom.global.kafka;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record RoomActivityMessage(
        String messageId,
        Long roomId,
        RoomActivityType type,
        Instant occurredAt,
        Map<String, Object> payload
) {
    public static RoomActivityMessage of(Long roomId, RoomActivityType type, Map<String, Object> payload) {
        return new RoomActivityMessage(UUID.randomUUID().toString(), roomId, type, Instant.now(), payload);
    }
}
