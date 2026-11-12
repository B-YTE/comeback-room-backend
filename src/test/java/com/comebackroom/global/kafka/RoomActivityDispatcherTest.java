package com.comebackroom.global.kafka;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("room-activity 처리기 분배")
class RoomActivityDispatcherTest {

    private final RecordingHandler chatHandler = new RecordingHandler(RoomActivityType.CHAT);
    private final RecordingHandler reactionHandler = new RecordingHandler(RoomActivityType.REACTION);
    private final RecordingHandler roomCloseHandler = new RecordingHandler(RoomActivityType.ROOM_CLOSED);

    @Test
    @DisplayName("메시지 type에 맞는 처리기만 호출한다")
    void dispatchToHandlerOfMessageType() {
        RoomActivityDispatcher dispatcher = new RoomActivityDispatcher(
                List.of(chatHandler, reactionHandler, roomCloseHandler));
        RoomActivityMessage message = RoomActivityMessage.of(7L, RoomActivityType.REACTION, Map.of("seq", 1));

        dispatcher.dispatch(message);

        assertThat(reactionHandler.received).containsExactly(message);
        assertThat(chatHandler.received).isEmpty();
        assertThat(roomCloseHandler.received).isEmpty();
    }

    @Test
    @DisplayName("같은 type의 처리기가 두 개면 생성할 때 실패한다")
    void failWhenHandlersAreDuplicated() {
        RecordingHandler anotherChatHandler = new RecordingHandler(RoomActivityType.CHAT);

        assertThatThrownBy(() -> new RoomActivityDispatcher(
                List.of(chatHandler, anotherChatHandler, reactionHandler, roomCloseHandler)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CHAT");
    }

    @Test
    @DisplayName("처리기가 없는 type이 있으면 생성할 때 실패한다")
    void failWhenHandlerIsMissing() {
        assertThatThrownBy(() -> new RoomActivityDispatcher(List.of(chatHandler, reactionHandler)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ROOM_CLOSED");
    }

    @Test
    @DisplayName("type이 없는 메시지는 어떤 처리기에도 넘기지 않고 예외를 던진다")
    void rejectMessageWithoutType() {
        RoomActivityDispatcher dispatcher = new RoomActivityDispatcher(
                List.of(chatHandler, reactionHandler, roomCloseHandler));
        RoomActivityMessage message = new RoomActivityMessage("message-1", 7L, null, Instant.now(), Map.of());

        assertThatThrownBy(() -> dispatcher.dispatch(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("message-1");
        assertThat(chatHandler.received).isEmpty();
        assertThat(reactionHandler.received).isEmpty();
        assertThat(roomCloseHandler.received).isEmpty();
    }

    private static class RecordingHandler implements RoomActivityHandler {

        private final RoomActivityType type;
        private final List<RoomActivityMessage> received = new ArrayList<>();

        RecordingHandler(RoomActivityType type) {
            this.type = type;
        }

        @Override
        public RoomActivityType supportedType() {
            return type;
        }

        @Override
        public void handle(RoomActivityMessage message) {
            received.add(message);
        }
    }
}
