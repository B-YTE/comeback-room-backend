package com.comebackroom.global.kafka;

import com.comebackroom.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.KafkaListener;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;

import static com.comebackroom.global.config.KafkaTopicConfig.ROOM_ACTIVITY_TOPIC;
import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@DisplayName("room-activity 발행/수신")
class RoomActivityKafkaIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class KafkaTestConfiguration {

        @Bean
        @ServiceConnection
        KafkaContainer kafkaContainer() {
            return new KafkaContainer(DockerImageName.parse("apache/kafka:4.0.0"));
        }

        @Bean
        RecordingConsumer recordingConsumer() {
            return new RecordingConsumer();
        }
    }

    static class RecordingConsumer {

        private final List<RoomActivityMessage> received = new CopyOnWriteArrayList<>();

        @KafkaListener(topics = ROOM_ACTIVITY_TOPIC, groupId = "test-recorder")
        void record(RoomActivityMessage message) {
            received.add(message);
        }

        List<RoomActivityMessage> awaitMessages(int expectedCount, Duration timeout) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeout.toMillis();
            while (received.size() < expectedCount && System.currentTimeMillis() < deadline) {
                Thread.sleep(100);
            }
            return List.copyOf(received);
        }
    }

    @Autowired
    private RoomActivityProducer producer;

    @Autowired
    private RecordingConsumer recorder;

    @Test
    @DisplayName("같은 roomId의 메시지는 발행한 순서대로 수신된다")
    void sameRoomMessagesAreReceivedInOrder() throws InterruptedException {
        int count = 100;
        for (int i = 0; i < count; i++) {
            producer.send(RoomActivityMessage.of(7L, RoomActivityType.CHAT, Map.of("seq", i)));
            producer.send(RoomActivityMessage.of(8L, RoomActivityType.REACTION, Map.of("seq", i)));
        }

        List<RoomActivityMessage> all = recorder.awaitMessages(count * 2, Duration.ofSeconds(30));
        assertThat(all).hasSize(count * 2);

        List<Object> expected = IntStream.range(0, count).boxed().map(i -> (Object) i).toList();
        assertThat(sequenceOf(all, 7L)).isEqualTo(expected);
        assertThat(sequenceOf(all, 8L)).isEqualTo(expected);
    }

    private List<Object> sequenceOf(List<RoomActivityMessage> messages, Long roomId) {
        return messages.stream()
                .filter(m -> m.roomId().equals(roomId))
                .map(m -> m.payload().get("seq"))
                .toList();
    }
}
