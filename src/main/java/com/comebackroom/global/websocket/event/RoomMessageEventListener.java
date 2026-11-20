package com.comebackroom.global.websocket.event;

import com.comebackroom.global.websocket.message.RoomMessageSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class RoomMessageEventListener {

    private final RoomMessageSender roomMessageSender;

    // 트랜잭션이 커밋된 뒤 실행하고 트랜잭션이 없으면 바로 실행
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void sendAfterCommit(RoomMessageEvent event) {
        try {
            roomMessageSender.sendToRoom(event.type(), event.roomId(), event.payload());
        } catch (RuntimeException e) {
            log.error("시청방 메시지 전송 실패: roomId={}, type={}", event.roomId(), event.type(), e);
        }
    }
}
