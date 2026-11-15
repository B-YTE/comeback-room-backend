package com.comebackroom.global.kafka;

import com.comebackroom.KafkaTestcontainersConfiguration;
import com.comebackroom.TestcontainersConfiguration;
import com.comebackroom.domain.chat.handler.ChatActivityHandler;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.comebackroom.global.config.KafkaTopicConfig.ROOM_ACTIVITY_DLT_TOPIC;
import static com.comebackroom.global.config.KafkaTopicConfig.ROOM_ACTIVITY_TOPIC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
@SpringBootTest
@DisplayName("room-activity Consumer 처리와 실패 처리")
public class RoomActivityConsumerIntegrationTest {

    private static final long TIMEOUT_MILLIS = 15_000L;

    @TestConfiguration(proxyBeanMethods = false)
    static class DltRecorderConfiguration {

        @Bean
        DltRecorder dltRecorder() {
            return new DltRecorder();
        }
    }

    static class DltRecorder {

        private final BlockingQueue<ConsumerRecord<String, String>> records = new LinkedBlockingQueue<>();

        @KafkaListener(
                topics = ROOM_ACTIVITY_DLT_TOPIC,
                groupId = "test-dlt-recorder",
                properties = "value.deserializer=org.apache.kafka.common.serialization.StringDeserializer")
        void record(ConsumerRecord<String, String> record) {
            records.add(record);
        }

        ConsumerRecord<String, String> next() throws InterruptedException {
            return records.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        }
    }

    @Autowired
    private RoomActivityProducer producer;

    @Autowired
    private ProducerFactory<Object, Object> producerFactory;

    @Autowired
    private DltRecorder dltRecorder;

    @MockitoSpyBean
    private ChatActivityHandler chatHandler;

    @Test
    @DisplayName("발행한 메시지는 type에 맞는 처리기까지 전달된다")
    void deliverPublishedMessageToHandler() {
        RoomActivityMessage message = RoomActivityMessage.of(7L, RoomActivityType.CHAT, Map.of("content", "안녕하세요"));

        producer.send(message);

        verify(chatHandler, timeout(TIMEOUT_MILLIS)).handle(sameMessageAs(message));
    }

    @Test
    @DisplayName("처리에 3번 실패한 메시지는 DLT로 이동한다")
    void moveToDltAfterThreeFailures() throws InterruptedException {
        RoomActivityMessage message = RoomActivityMessage.of(7L, RoomActivityType.CHAT, Map.of("content", "실패할 메시지"));
        willThrow(new IllegalStateException("테스트용 처리 실패"))
                .given(chatHandler).handle(sameMessageAs(message));

        producer.send(message);

        ConsumerRecord<String, String> dltRecord = dltRecorder.next();
        assertThat(dltRecord).isNotNull();
        assertThat(dltRecord.value()).contains(message.messageId());
        assertThat(headerValue(dltRecord, KafkaHeaders.DLT_ORIGINAL_TOPIC)).isEqualTo(ROOM_ACTIVITY_TOPIC);
        verify(chatHandler, times(3)).handle(sameMessageAs(message));
    }

    @Test
    @DisplayName("형식이 깨진 메시지는 재시도하지 않고 원본 그대로 DLT로 이동한다")
    void moveMalformedMessageToDltWithoutRetry() throws InterruptedException {
        Map<String, Object> stringValueOverrides =
                Map.of(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        KafkaTemplate<Object, Object> rawTemplate = new KafkaTemplate<>(producerFactory, stringValueOverrides);
        String malformedJson = "{not-json";

        rawTemplate.send(ROOM_ACTIVITY_TOPIC, "7", malformedJson);

        ConsumerRecord<String, String> dltRecord = dltRecorder.next();
        assertThat(dltRecord).isNotNull();
        assertThat(dltRecord.value()).isEqualTo(malformedJson);
        verify(chatHandler, never()).handle(any());
    }

    private static RoomActivityMessage sameMessageAs(RoomActivityMessage expected) {
        return argThat(actual -> actual.messageId().equals(expected.messageId()));
    }

    private static String headerValue(ConsumerRecord<String, String> record, String headerName) {
        Header header = record.headers().lastHeader(headerName);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
