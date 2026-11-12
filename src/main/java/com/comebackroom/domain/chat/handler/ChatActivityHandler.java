package com.comebackroom.domain.chat.handler;

import com.comebackroom.global.kafka.RoomActivityHandler;
import com.comebackroom.global.kafka.RoomActivityMessage;
import com.comebackroom.global.kafka.RoomActivityType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
@Slf4j
@Component
public class ChatActivityHandler implements RoomActivityHandler {

    @Override
    public RoomActivityType supportedType() {
        return RoomActivityType.CHAT;
    }

    @Override
    public void handle(RoomActivityMessage message) {
        // TODO: 채팅 저장 후 시청방에 전송
        log.debug("CHAT 처리기 호출: roomId={}, messageId={}", message.roomId(), message.messageId());
    }
}
