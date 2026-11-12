package com.comebackroom.domain.reaction.handler;

import com.comebackroom.global.kafka.RoomActivityHandler;
import com.comebackroom.global.kafka.RoomActivityMessage;
import com.comebackroom.global.kafka.RoomActivityType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
@Slf4j
@Component
public class ReactionActivityHandler implements RoomActivityHandler {

    @Override
    public RoomActivityType supportedType() {
        return RoomActivityType.REACTION;
    }

    @Override
    public void handle(RoomActivityMessage message) {
        // TODO: 리액션 저장 후 시청방에 전송
        log.debug("REACTION 처리기 호출: roomId={}, messageId={}", message.roomId(), message.messageId());
    }
}
