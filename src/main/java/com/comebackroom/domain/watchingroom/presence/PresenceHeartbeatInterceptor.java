package com.comebackroom.domain.watchingroom.presence;

import com.comebackroom.global.exception.BusinessException;
import com.comebackroom.global.security.principal.MemberPrincipal;
import com.comebackroom.global.websocket.exception.WebSocketErrorCode;
import com.comebackroom.global.websocket.interceptor.StompAuthChannelInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PresenceHeartbeatInterceptor implements ChannelInterceptor {

    private final RoomPresenceStore presenceStore;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        // 하트비트가 아닌 메시지는 통과
        if (accessor == null || accessor.getMessageType() != SimpMessageType.HEARTBEAT) {
            return message;
        }

        String sessionId = accessor.getSessionId();
        Long memberId = toMemberId(accessor.getUser());

        Map<String, Object> attributes = accessor.getSessionAttributes();
        Long roomId = attributes == null ? null
                : toRoomId(attributes.get(StompAuthChannelInterceptor.ROOM_ID_ATTRIBUTE));

        if (sessionId == null || memberId == null || roomId == null) {
            log.warn("하트비트에서 접속 정보를 찾지 못했습니다: sessionId={}", sessionId);
            return message;
        }

        boolean registered;
        try {
            registered = presenceStore.heartbeat(roomId, memberId, sessionId);
        } catch (RuntimeException e) {
            log.error("하트비트 갱신 실패: roomId={}, memberId={}, sessionId={}",
                    roomId, memberId, sessionId, e);
            return message;
        }

        if (!registered) {
            throw new BusinessException(WebSocketErrorCode.INVALID_TICKET);
        }

        return message;
    }

    private Long toMemberId(Principal user) {
        return user instanceof MemberPrincipal principal ? principal.memberId() : null;
    }

    private Long toRoomId(Object value) {
        return value instanceof Long roomId ? roomId : null;
    }

}
