package com.comebackroom.global.kafka;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import static com.comebackroom.global.config.KafkaTopicConfig.ROOM_ACTIVITY_TOPIC;

@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
@Component
@RequiredArgsConstructor
public class RoomActivityProducer {

    private final KafkaTemplate<String, RoomActivityMessage> kafkaTemplate;

    public void send(RoomActivityMessage message) {
        kafkaTemplate.send(ROOM_ACTIVITY_TOPIC, String.valueOf(message.roomId()), message);
    }
}
