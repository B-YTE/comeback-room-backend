package com.comebackroom.global.websocket.message;

import com.comebackroom.global.exception.GlobalErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("시청방 메시지 전송 컴포넌트")
public class RoomMessageSenderTest {

    @Mock
    private SimpMessageSendingOperations messagingTemplate;

    @InjectMocks
    private RoomMessageSender roomMessageSender;

    @Test
    @DisplayName("방 메시지는 type에 맞는 구독 주소로 보낸다")
    void sendToRoom_sendsToTypeDestination() {
        // given
        Map<String, Object> payload = Map.of("content", "컴백룸 최고");

        // when
        roomMessageSender.sendToRoom(RoomMessageType.CHAT_MESSAGE, 15L, payload);

        // then
        ArgumentCaptor<RoomMessage> captor = ArgumentCaptor.forClass(RoomMessage.class);
        verify(messagingTemplate).convertAndSend(eq("/sub/watching-rooms/15/chats"), captor.capture());

        RoomMessage sent = captor.getValue();
        assertThat(sent.type()).isEqualTo(RoomMessageType.CHAT_MESSAGE);
        assertThat(sent.roomId()).isEqualTo(15L);
        assertThat(sent.payload()).isEqualTo(payload);
        assertThat(sent.serverTime()).isNotNull();
    }

    @Test
    @DisplayName("오류는 해당 회원의 개인 오류 큐로 보낸다")
    void sendError_sendsToMemberErrorQueue() {
        // when
        roomMessageSender.sendError(7L, GlobalErrorCode.INVALID_INPUT_VALUE, "/pub/watching-rooms/15/chats");

        // then
        ArgumentCaptor<RoomErrorMessage> captor = ArgumentCaptor.forClass(RoomErrorMessage.class);
        verify(messagingTemplate).convertAndSendToUser(eq("7"), eq("/sub/errors"), captor.capture());

        RoomErrorMessage sent = captor.getValue();
        assertThat(sent.code()).isEqualTo("INVALID_INPUT_VALUE");
        assertThat(sent.message()).isEqualTo("잘못된 입력값입니다.");
        assertThat(sent.destination()).isEqualTo("/pub/watching-rooms/15/chats");
        assertThat(sent.timestamp()).isNotNull();
    }

    @Test
    @DisplayName("memberId가 없으면 보내지 않고 바로 실패한다")
    void sendError_withoutMemberId_fails() {
        assertThatThrownBy(() -> roomMessageSender.sendError(
                null, GlobalErrorCode.INVALID_INPUT_VALUE, "/pub/watching-rooms/15/chats"))
                .isInstanceOf(NullPointerException.class);

        verifyNoInteractions(messagingTemplate);
    }
}
