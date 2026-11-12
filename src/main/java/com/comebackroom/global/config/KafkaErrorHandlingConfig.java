package com.comebackroom.global.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.databind.ser.jdk.ByteArraySerializer;

import java.util.LinkedHashMap;
import java.util.Map;

@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
@Configuration
public class KafkaErrorHandlingConfig {

    private static final long RETRY_INTERVAL_MILLIS = 1_000L;
    private static final long MAX_RETRIES = 2L;

    @Bean
    public DefaultErrorHandler kafkaErrorHandling(KafkaTemplate<Object, Object> kafkaTemplate,
                                                  ProducerFactory<Object, Object> producerFactory) {
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(deadLetterTemplates(kafkaTemplate, producerFactory));

        DefaultErrorHandler errorHandler =
                new DefaultErrorHandler(recoverer, new FixedBackOff(RETRY_INTERVAL_MILLIS, MAX_RETRIES)); // 1초 간격 2번 재시도
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);
        return errorHandler;
    }

    private Map<Class<?>, KafkaOperations<?, ?>> deadLetterTemplates(KafkaTemplate<Object, Object> kafkaTemplate,
                                                                     ProducerFactory<Object, Object> producerFactory) {
        Map<String, Object> bytesOverrides =
                Map.of(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        KafkaTemplate<Object, Object> bytesTemplate = new KafkaTemplate<>(producerFactory, bytesOverrides);

        Map<Class<?>, KafkaOperations<?, ?>> templates = new LinkedHashMap<>();
        templates.put(byte[].class, bytesTemplate);
        templates.put(Object.class, kafkaTemplate);
        return templates;
    }
}
