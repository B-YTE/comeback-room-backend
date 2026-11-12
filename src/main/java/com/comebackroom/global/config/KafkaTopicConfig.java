package com.comebackroom.global.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
@Configuration
public class KafkaTopicConfig {

    public static final String ROOM_ACTIVITY_TOPIC = "room-activity";

    @Bean
    public NewTopic roomActivityTopic() {
        return TopicBuilder.name(ROOM_ACTIVITY_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
