package com.comebackroom.domain.report.handler;

import com.comebackroom.global.kafka.RoomActivityHandler;
import com.comebackroom.global.kafka.RoomActivityMessage;
import com.comebackroom.global.kafka.RoomActivityType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
@Slf4j
@Component
public class RoomClosedActivityHandler implements RoomActivityHandler {

    @Override
    public RoomActivityType supportedType() {
        return RoomActivityType.ROOM_CLOSED;
    }

    @Override
    public void handle(RoomActivityMessage message) {
        // TODO: roomId로 리포트 존재 여부 확인 후 리포트 생성
        log.debug("ROOM_CLOSED 처리기 호출: roomId={}, messageId={}", message.roomId(), message.messageId());
    }
}
