package com.comebackroom.global.websocket.interceptor;

import com.comebackroom.global.exception.BusinessException;
import com.comebackroom.global.security.principal.MemberPrincipal;
import com.comebackroom.global.websocket.exception.WebSocketErrorCode;
import com.comebackroom.global.websocket.ticket.TicketInfo;
import com.comebackroom.global.websocket.ticket.WebSocketTicketStore;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;


import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    public static final String ROOM_ID_ATTRIBUTE = "roomId";

    private static final String TICKET_HEADER = "ticket";

    private static final String APPLICATION_DESTINATION_PREFIX = "/pub/";
    private static final Pattern ROOM_SUBSCRIBE_DESTINATION =
            Pattern.compile("^/sub/watching-rooms/(\\d{1,18})/[a-z-]+$");

    private final WebSocketTicketStore ticketStore;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        switch (accessor.getCommand()) {
            case CONNECT, STOMP -> authenticate(accessor);
            case SUBSCRIBE -> authorizeSubscribe(accessor);
            case SEND -> validateSendDestination(accessor);
            default -> {
            }
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        TicketInfo ticketInfo = ticketStore.consume(
                accessor.getFirstNativeHeader(TICKET_HEADER)
        ).orElseThrow(() -> new BusinessException(WebSocketErrorCode.INVALID_TICKET));
        accessor.setUser(new MemberPrincipal(ticketInfo.memberId())); // 해당 세션(연결)의 사용자 식별자 기록
        Objects.requireNonNull(accessor.getSessionAttributes(), "STOMP 세션 속성이 없습니다.")
                .put(ROOM_ID_ATTRIBUTE, ticketInfo.roomId()); // 해당 세션(연결)의 방 식별자 기록
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        Matcher matcher = ROOM_SUBSCRIBE_DESTINATION.matcher(requireDestination(accessor));
        if (!matcher.matches()) { // 허용 목록에 없는 경로는 모두 거부
            throw new BusinessException(WebSocketErrorCode.INVALID_DESTINATION);
        }

        // 경로의 방과 ticket으로 연결한 방이 다르면 거부
        Long requestedRoomId = Long.valueOf(matcher.group(1));
        Object connectedRoomId = Objects.requireNonNull(
                accessor.getSessionAttributes(), "STOMP 세션 속성이 없습니다."
        ).get(ROOM_ID_ATTRIBUTE);
        if (!requestedRoomId.equals(connectedRoomId)) {
            throw new BusinessException(WebSocketErrorCode.NOT_ROOM_PARTICIPANT);
        }
    }

    private void validateSendDestination(StompHeaderAccessor accessor) {
        String destination = requireDestination(accessor);
        if (!destination.startsWith(APPLICATION_DESTINATION_PREFIX)) {
            throw new BusinessException(WebSocketErrorCode.INVALID_DESTINATION);
        }
    }

    // 헤더에 destination이 존재하지 않으면 예외
    private String requireDestination(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            throw new BusinessException(WebSocketErrorCode.INVALID_DESTINATION);
        }
        return destination;
    }

}
