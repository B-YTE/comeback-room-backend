package com.comebackroom.global.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import static com.comebackroom.global.config.KafkaTopicConfig.ROOM_ACTIVITY_TOPIC;

@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
@Slf4j
@Component
@RequiredArgsConstructor
public class RoomActivityConsumer {

    private final RoomActivityDispatcher dispatcher;

    @KafkaListener(topics = ROOM_ACTIVITY_TOPIC)
    public void consume(RoomActivityMessage message) {
        log.debug("room-activity 수신: roomId={}, type={}, messageId={}",
                message.roomId(), message.type(), message.messageId());
        dispatcher.dispatch(message);
    }
}
