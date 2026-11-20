package com.comebackroom.global.websocket.event;

import com.comebackroom.KafkaTestcontainersConfiguration;
import com.comebackroom.TestcontainersConfiguration;
import com.comebackroom.global.websocket.message.RoomMessageSender;
import com.comebackroom.global.websocket.message.RoomMessageType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
@SpringBootTest
@DisplayName("커밋 후 시청방 메시지 전송")
public class RoomMessageEventListenerTest {

    private static final Long ROOM_ID = 15L;
    private static final Map<String, Object> PAYLOAD = Map.of("content", "컴백룸 최고");
    private static final RoomMessageEvent EVENT =
            new RoomMessageEvent(RoomMessageType.CHAT_MESSAGE, ROOM_ID, PAYLOAD);

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private RoomMessageSender roomMessageSender;

    @Test
    @DisplayName("트랜잭션이 커밋되면 메시지를 보낸다")
    void commit_sendsMessage() {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(EVENT));

        verify(roomMessageSender).sendToRoom(RoomMessageType.CHAT_MESSAGE, ROOM_ID, PAYLOAD);
    }

    @Test
    @DisplayName("트랜잭션이 롤백되면 메시지를 보내지 않는다")
    void rollback_doesNotSendMessage() {
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(EVENT);
            status.setRollbackOnly();
        });

        verify(roomMessageSender, never()).sendToRoom(RoomMessageType.CHAT_MESSAGE, ROOM_ID, PAYLOAD);
    }

    @Test
    @DisplayName("트랜잭션이 없으면 바로 보낸다")
    void noTransaction_sendsImmediately() {
        eventPublisher.publishEvent(EVENT);

        verify(roomMessageSender).sendToRoom(RoomMessageType.CHAT_MESSAGE, ROOM_ID, PAYLOAD);
    }

    @Test
    @DisplayName("전송이 실패해도 이벤트를 발행한 쪽으로 예외가 퍼지지 않는다")
    void sendFailure_doesNotPropagate() {
        willThrow(new IllegalStateException("전송 실패"))
                .given(roomMessageSender).sendToRoom(any(), any(), any());

        assertThatCode(() -> eventPublisher.publishEvent(EVENT))
                .doesNotThrowAnyException();
    }
}
