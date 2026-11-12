package com.comebackroom.global.kafka;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
@Component
public class RoomActivityDispatcher {

    private final Map<RoomActivityType, RoomActivityHandler> handlers;

    public RoomActivityDispatcher(List<RoomActivityHandler> handlers) {
        this.handlers = mapByType(handlers);
    }

    public void dispatch(RoomActivityMessage message) {
        RoomActivityType type = message.type();
        if (type == null) {
            throw new IllegalArgumentException(
                    "type이 없는 room-activity 메시지입니다. messageId=" + message.messageId());
        }
        handlers.get(type).handle(message);
    }

    private static Map<RoomActivityType, RoomActivityHandler> mapByType(List<RoomActivityHandler> handlers) {
        Map<RoomActivityType, RoomActivityHandler> byType = new EnumMap<>(RoomActivityType.class);

        for (RoomActivityHandler handler : handlers) {
            RoomActivityHandler previous = byType.put(handler.supportedType(), handler);
            if (previous != null) {
                throw new IllegalStateException("room-activity 처리기가 중복됐습니다. type=" + handler.supportedType()
                + ", handlers=[" + previous.getClass().getSimpleName()
                + ", " + handler.getClass().getSimpleName() + "]");
            }
        }

        List<RoomActivityType> missingTypes = Arrays.stream(RoomActivityType.values())
                .filter(type -> !byType.containsKey(type))
                .toList();
        if (!missingTypes.isEmpty()) {
            throw new IllegalStateException("처리기가 없는 room-activity type이 있습니다. types=" + missingTypes);
        }

        return byType;
    }
}
