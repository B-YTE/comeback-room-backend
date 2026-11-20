package com.comebackroom.global.websocket.interceptor;

import com.comebackroom.global.exception.BusinessException;
import com.comebackroom.global.exception.ErrorCode;
import com.comebackroom.global.security.principal.MemberPrincipal;
import com.comebackroom.global.websocket.exception.WebSocketErrorCode;
import com.comebackroom.global.websocket.ticket.TicketInfo;
import com.comebackroom.global.websocket.ticket.WebSocketTicketStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import java.util.Optional;

import static com.comebackroom.global.websocket.interceptor.StompAuthChannelInterceptor.ROOM_ID_ATTRIBUTE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
public class StompAuthChannelInterceptorTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long ROOM_ID = 1L;
    private static final Long OTHER_ROOM_ID = 16L;
    private static final String TICKET = "valid-ticket";
    private static final String INVALID_TICKET = "invalid-ticket";

    @Mock
    private WebSocketTicketStore ticketStore;
    @Mock
    private MessageChannel channel;

    @InjectMocks
    private StompAuthChannelInterceptor interceptor;



    @Nested
    @DisplayName("CONNECT")
    class Connect {

        @Test
        @DisplayName("유효한 ticket으로 연결하면 회원과 방 정보를 연결에 기록한다")
        void connectWithValidTicket_recordsMemberAndRoom() {
            // given
            given(ticketStore.consume(TICKET))
                    .willReturn(Optional.of(new TicketInfo(MEMBER_ID, ROOM_ID)));
            StompHeaderAccessor accessor = accessorOf(StompCommand.CONNECT, new HashMap<>());
            accessor.setNativeHeader("ticket", TICKET);
            Message<?> message = messageOf(accessor);

            // when
            Message<?> result = interceptor.preSend(message, channel);

            // then
            assertThat(result).isSameAs(message);
            assertThat(accessor.getUser()).isEqualTo(new MemberPrincipal(MEMBER_ID));
            assertThat(accessor.getSessionAttributes()).containsEntry(ROOM_ID_ATTRIBUTE, ROOM_ID);
        }

        @Test
        @DisplayName("저장소에 없는 ticket으로 연결하면 거부한다")
        void connectWithUnknownTicket_isRejected() {
            // given
            given(ticketStore.consume(INVALID_TICKET)).willReturn(Optional.empty());
            StompHeaderAccessor accessor = accessorOf(StompCommand.CONNECT, new HashMap<>());
            accessor.setNativeHeader("ticket", INVALID_TICKET);

            // when & then
            assertRejected(messageOf(accessor), WebSocketErrorCode.INVALID_TICKET);
        }

        @Test
        @DisplayName("CONNECT의 별칭인 STOMP 명령으로 연결해도 ticket을 검증한다")
        void connectWithStompCommand_validatesTicket() {
            // given
            given(ticketStore.consume(INVALID_TICKET)).willReturn(Optional.empty());
            StompHeaderAccessor accessor = accessorOf(StompCommand.STOMP, new HashMap<>());
            accessor.setNativeHeader("ticket", INVALID_TICKET);

            // when & then
            assertRejected(messageOf(accessor), WebSocketErrorCode.INVALID_TICKET);
        }
    }

    @Nested
    @DisplayName("SUBSCRIBE")
    class Subscribe {

        @Test
        @DisplayName("연결한 시청방의 경로는 구독 가능하다")
        void subscribeToConnectedRoom_isAllowed() {
            // given
            StompHeaderAccessor accessor = accessorOf(StompCommand.SUBSCRIBE, connectedTo(ROOM_ID));
            accessor.setDestination("/sub/watching-rooms/" + ROOM_ID + "/events");
            Message<?> message = messageOf(accessor);

            // when
            Message<?> result = interceptor.preSend(message, channel);

            // then
            assertThat(result).isSameAs(message);
        }

        @Test
        @DisplayName("개인 오류 큐는 구독 가능하다")
        void subscribeToUserErrorQueue_isAllowed() {
            // given
            StompHeaderAccessor accessor = accessorOf(StompCommand.SUBSCRIBE, connectedTo(ROOM_ID));
            accessor.setDestination("/user/sub/errors");
            Message<?> message = messageOf(accessor);

            // when
            Message<?> result = interceptor.preSend(message, channel);

            // then
            assertThat(result).isSameAs(message);
        }

        @Test
        @DisplayName("연결한 방과 다른 시청방 구독은 거부한다")
        void subscribeToOtherRoom_isRejected() {
            // given
            StompHeaderAccessor accessor = accessorOf(StompCommand.SUBSCRIBE, connectedTo(ROOM_ID));
            accessor.setDestination("/sub/watching-rooms/" + OTHER_ROOM_ID + "/events");

            // when & then
            assertRejected(messageOf(accessor), WebSocketErrorCode.NOT_ROOM_PARTICIPANT);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "/sub/watching-rooms/*/events",
                "/sub/**",
                "/sub/watching-rooms/12345678901234567890/events",
                "/user/sub/errors/extra",
                "/user/sub/chats",
                "/sub/errors-user1234"
        })
        @DisplayName("허용되지 않는 형식의 경로의 구독은 거부한다")
        void subscribeToInvalidDestination_isRejected(String destination) {
            // given
            StompHeaderAccessor accessor = accessorOf(StompCommand.SUBSCRIBE, connectedTo(ROOM_ID));
            accessor.setDestination(destination);

            // when & then
            assertRejected(messageOf(accessor), WebSocketErrorCode.INVALID_DESTINATION);
        }

        @Test
        @DisplayName("destination 없는 구독은 거부한다")
        void subscribeWithoutDestination_isRejected() {
            // given
            StompHeaderAccessor accessor = accessorOf(StompCommand.SUBSCRIBE, connectedTo(ROOM_ID));

            // when & then
            assertRejected(messageOf(accessor), WebSocketErrorCode.INVALID_DESTINATION);
        }
    }


    private StompHeaderAccessor accessorOf(
            StompCommand command, Map<String, Object> sessionAttributes
    ) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionAttributes(sessionAttributes);
        accessor.setLeaveMutable(true);
        return accessor;
    }

    private Message<byte[]> messageOf(StompHeaderAccessor accessor) {
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Map<String, Object> connectedTo(Long roomId) {
        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put(ROOM_ID_ATTRIBUTE, roomId);
        return sessionAttributes;
    }

    private void assertRejected(Message<?> message, ErrorCode expected) {
        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }
}
