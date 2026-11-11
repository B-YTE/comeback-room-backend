package com.comebackroom.global.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import static com.comebackroom.global.config.KafkaTopicConfig.ROOM_ACTIVITY_TOPIC;

@Slf4j
@Component
public class RoomActivityConsumer {

    @KafkaListener(topics = ROOM_ACTIVITY_TOPIC)
    public void consume(RoomActivityMessage message) {
        log.info("room-activity 수신: roomId={}, type={}, messageId={}",
                message.roomId(), message.type(), message.messageId());
    }
}
