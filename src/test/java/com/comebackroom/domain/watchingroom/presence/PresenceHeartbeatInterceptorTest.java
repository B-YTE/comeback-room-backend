package com.comebackroom.domain.watchingroom.presence;

import com.comebackroom.global.exception.BusinessException;
import com.comebackroom.global.security.principal.MemberPrincipal;
import com.comebackroom.global.websocket.exception.WebSocketErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.util.HashMap;
import java.util.Map;

import static com.comebackroom.global.websocket.interceptor.StompAuthChannelInterceptor.ROOM_ID_ATTRIBUTE;
import static com.comebackroom.global.websocket.interceptor.StompAuthChannelInterceptor.TAB_ID_ATTRIBUTE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
public class PresenceHeartbeatInterceptorTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long ROOM_ID = 100L;
    private static final String TAB_ID = "tab-1";

    @Mock
    private RoomPresenceStore presenceStore;
    @Mock
    private MessageChannel channel;

    @InjectMocks
    private PresenceHeartbeatInterceptor interceptor;

    @Test
    @DisplayName("스토어가 모르는 세션의 하트비트는 거부한다")
    void heartbeatOfUnknownSession_isRejected() {
        given(presenceStore.heartbeat(ROOM_ID, MEMBER_ID, TAB_ID)).willReturn(false);
        Message<?> message = heartbeatMessage();

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(WebSocketErrorCode.INVALID_TICKET));
    }

    @Test
    @DisplayName("Redis 장애로 갱신에 실패해도 연결은 끊지 않고 유지한다")
    void heartbeatWhenStoreFails_keepsConnection() {
        given(presenceStore.heartbeat(ROOM_ID, MEMBER_ID, TAB_ID))
                .willThrow(new IllegalStateException("redis down"));
        Message<?> message = heartbeatMessage();

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isSameAs(message);
    }

    @Test
    @DisplayName("하트비트가 아닌 메시지는 접속 상태를 건드리지 않고 통과시킨다")
    void nonHeartbeatMessage_passesWithoutTouchingStore() {
        Message<?> message = messageOf(StompHeaderAccessor.create(StompCommand.SEND));

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isSameAs(message);
        then(presenceStore).shouldHaveNoInteractions();
    }

    private Message<?> heartbeatMessage() {
        return messageOf(StompHeaderAccessor.createForHeartbeat());
    }

    private Message<?> messageOf(StompHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put(ROOM_ID_ATTRIBUTE, ROOM_ID);
        sessionAttributes.put(TAB_ID_ATTRIBUTE, TAB_ID);

        accessor.setUser(new MemberPrincipal(MEMBER_ID));
        accessor.setSessionAttributes(sessionAttributes);
        accessor.setLeaveMutable(true);

        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

}
