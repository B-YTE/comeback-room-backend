package com.comebackroom.domain.watchingroom.presence;

import com.comebackroom.global.security.principal.MemberPrincipal;
import com.comebackroom.global.websocket.interceptor.StompAuthChannelInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpAttributes;
import org.springframework.messaging.simp.SimpAttributesContextHolder;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Slf4j
@Component
public class RoomPresenceEventListener {

    // 연결이 끊긴 이후 시간 안에 같은 회원이 다시 연결하면 접속 상태 유지
    static final Duration DISCONNECT_GRACE = Duration.ofSeconds(10);

    private final RoomPresenceStore presenceStore;
    private final TaskScheduler presenceTaskScheduler;

    public RoomPresenceEventListener(
            RoomPresenceStore presenceStore,
            @Qualifier("presenceTaskScheduler") TaskScheduler presenceTaskScheduler
            ) {
        this.presenceStore = presenceStore;
        this.presenceTaskScheduler = presenceTaskScheduler;
    }

    // CONNECTED 프레임을 보낼 때 발행
    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        String sessionId = StompHeaderAccessor.wrap(event.getMessage()).getSessionId();
        Long memberId = toMemberId(event.getUser());
        SimpAttributes sessionAttributes = SimpAttributesContextHolder.getAttributes();
        Long roomId = sessionAttributes == null ? null
                : toRoomId(sessionAttributes.getAttribute(StompAuthChannelInterceptor.ROOM_ID_ATTRIBUTE));
        String tabId = sessionAttributes == null ? null
                : toTabId(sessionAttributes.getAttribute(StompAuthChannelInterceptor.TAB_ID_ATTRIBUTE));

        if (memberId == null || roomId == null || tabId == null) {
            log.error("연결 성공 이벤트에서 접속 정보를 찾지 못했습니다: sessionId={}", sessionId);
            return;
        }

        try {
            presenceStore.connect(roomId, memberId, tabId);
        } catch (RuntimeException e) {
            log.error("접속 상태 기록 실패: roomId={}, memberId={}, tabId={}, sessionId={}",
                    roomId, memberId, tabId, sessionId, e);
        }
    }

    // 웹소켓이 닫힐 때 발행
    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        Long memberId = toMemberId(event.getUser());
        Map<String, Object> attributes = StompHeaderAccessor.wrap(event.getMessage())
                .getSessionAttributes();
        Long roomId = attributes == null
                ? null
                : toRoomId(attributes.get(StompAuthChannelInterceptor.ROOM_ID_ATTRIBUTE));
        String tabId = attributes == null
                ? null
                : toTabId(attributes.get(StompAuthChannelInterceptor.TAB_ID_ATTRIBUTE));

        if (memberId == null || roomId == null || tabId == null) {
            log.debug("인증되지 않은 연결 해제: sessionId={}, status={}",
                    sessionId, event.getCloseStatus());
            return;
        }

        Instant runAt = presenceTaskScheduler.getClock().instant().plus(DISCONNECT_GRACE);
        presenceTaskScheduler.schedule(() -> disconnectAfterGrace(roomId, memberId, tabId, sessionId), runAt);

    }

    private void disconnectAfterGrace(Long roomId, Long memberId, String tabId, String sessionId) {
        try {
            presenceStore.disconnect(roomId, memberId, tabId);
        } catch (RuntimeException e) {
            log.error("유예 후 접속 해제 실패: roomId={}, memberId={}, tabId={}, sessionId={}",
                    roomId, memberId, tabId, sessionId, e);
        }
    }

    private Long toMemberId(Principal user) {
        return user instanceof MemberPrincipal principal ? principal.memberId() : null;
    }

    private Long toRoomId(Object value) {
        return value instanceof Long roomId ? roomId : null;
    }

    private String toTabId(Object value) {
        return value instanceof String tabId ? tabId : null;
    }
}
