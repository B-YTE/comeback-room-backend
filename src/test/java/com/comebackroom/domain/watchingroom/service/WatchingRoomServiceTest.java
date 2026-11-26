package com.comebackroom.domain.watchingroom.service;

import com.comebackroom.domain.member.entity.Member;
import com.comebackroom.domain.member.repository.MemberRepository;
import com.comebackroom.domain.watchingroom.dto.request.JoinWatchingRoomRequest;
import com.comebackroom.domain.watchingroom.dto.response.JoinWatchingRoomResponse;
import com.comebackroom.domain.watchingroom.entity.RoomParticipant;
import com.comebackroom.domain.watchingroom.entity.WatchingRoom;
import com.comebackroom.domain.watchingroom.enums.RoomStatus;
import com.comebackroom.domain.watchingroom.exception.WatchingRoomErrorCode;
import com.comebackroom.domain.watchingroom.presence.RoomPresenceStore;
import com.comebackroom.domain.watchingroom.repository.RoomParticipantRepository;
import com.comebackroom.domain.watchingroom.repository.WatchingRoomRepository;
import com.comebackroom.global.exception.BusinessException;
import com.comebackroom.global.websocket.ticket.WebSocketTicketStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
public class WatchingRoomServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 1, 21, 0);
    private static final Long ROOM_ID = 1L;
    private static final Long MEMBER_ID = 10L;
    private static final String TAB_ID = "tab-1";
    private static final String TICKET = "ticket-1";
    private static final int MAX_PARTICIPANTS = 5;

    @Mock
    private RoomPresenceStore presenceStore;
    @Mock
    private WebSocketTicketStore ticketStore;
    @Mock
    private WatchingRoomRepository watchingRoomRepository;
    @Mock
    private RoomParticipantRepository roomParticipantRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private WatchingRoom room;
    @Mock
    private Member member;

    private WatchingRoomService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(KST).toInstant(), KST);
        service = new WatchingRoomService(
                clock, presenceStore, ticketStore,
                watchingRoomRepository, roomParticipantRepository, memberRepository
        );
    }

    @Nested
    @DisplayName("시청방 참가")
    class JoinRoom {

        @Test
        @DisplayName("비공개방에서 틀린 코드를 보내면 거부하고, 자리는 예약하지 않는다")
        void joinWithWrongCode_isRejectedWithoutReserving() {
            // given
            givenJoinableRoom(false);
            given(room.getPrivateCode()).willReturn("123456");

            // when & then
            assertThatThrownBy(() -> service.joinRoom(ROOM_ID, MEMBER_ID, new JoinWatchingRoomRequest("000000")))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(WatchingRoomErrorCode.INVALID_PRIVATE_CODE));
            then(presenceStore).should(never()).reserve(any(), any(), anyInt(), any());
        }

        @Test
        @DisplayName("비공개방이어도 방장은 코드 없이 참가한다")
        void hostJoinsPrivateRoomWithoutCode() {
            // given
            givenJoinableRoom(false);
            given(room.isHost(MEMBER_ID)).willReturn(true);
            givenReserve(true);
            givenTicketIssued();

            // when
            JoinWatchingRoomResponse response =
                    service.joinRoom(ROOM_ID, MEMBER_ID, new JoinWatchingRoomRequest(null));

            // then
            assertThat(response.ticket()).isEqualTo(TICKET);
        }

        @Test
        @DisplayName("비공개방이어도 이미 접속 중인 회원은 코드 없이 참가한다")
        void connectedMemberJoinsPrivateRoomWithoutCode() {
            // given
            givenJoinableRoom(false);
            given(room.getId()).willReturn(ROOM_ID);
            given(presenceStore.isConnected(ROOM_ID, MEMBER_ID)).willReturn(true);
            givenReserve(true);
            givenTicketIssued();

            // when
            JoinWatchingRoomResponse response =
                    service.joinRoom(ROOM_ID, MEMBER_ID, new JoinWatchingRoomRequest(null));

            // then
            assertThat(response.ticket()).isEqualTo(TICKET);
        }

        @Test
        @DisplayName("비공개방이어도 시청 기록이 있는 회원은 코드 없이 참가한다")
        void memberWithRecordJoinsPrivateRoomWithoutCode() {
            // given
            givenJoinableRoom(false);
            given(room.getId()).willReturn(ROOM_ID);
            given(roomParticipantRepository.existsByRoomIdAndMemberId(ROOM_ID, MEMBER_ID)).willReturn(true);
            givenReserve(true);
            givenTicketIssued();

            // when
            JoinWatchingRoomResponse response =
                    service.joinRoom(ROOM_ID, MEMBER_ID, new JoinWatchingRoomRequest(null));

            // then
            assertThat(response.ticket()).isEqualTo(TICKET);
        }

        @Test
        @DisplayName("정원이 가득 차면 거부하고 ticket을 발급하지 않는다")
        void joinWhenFull_isRejectedWithoutTicket() {
            // given
            givenJoinableRoom(true);
            givenReserve(false);

            // when & then
            assertThatThrownBy(() -> service.joinRoom(ROOM_ID, MEMBER_ID, new JoinWatchingRoomRequest(null)))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(WatchingRoomErrorCode.WATCHING_ROOM_FULL));
            then(ticketStore).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("방송 중에 참가하면 시청 기록을 저장한다")
        void joinWhileLive_recordsParticipation() {
            // given
            givenJoinableRoom(true);
            givenReserve(true);
            givenTicketIssued();
            givenLiveRoom();

            // when
            JoinWatchingRoomResponse response =
                    service.joinRoom(ROOM_ID, MEMBER_ID, new JoinWatchingRoomRequest(null));

            // then
            then(roomParticipantRepository).should().save(any(RoomParticipant.class));
            assertThat(response.status()).isEqualTo(RoomStatus.LIVE);
        }

        @Test
        @DisplayName("대기화면 단계에서 참가하면 시청 기록을 저장하지 않는다")
        void joinBeforeStart_doesNotRecordParticipation() {
            // given
            givenJoinableRoom(true);
            givenReserve(true);
            givenTicketIssued();

            // when
            JoinWatchingRoomResponse response =
                    service.joinRoom(ROOM_ID, MEMBER_ID, new JoinWatchingRoomRequest(null));

            // then
            then(roomParticipantRepository).should(never()).save(any());
            assertThat(response.status()).isEqualTo(RoomStatus.SCHEDULED);
        }

        @Test
        @DisplayName("시청 기록이 동시에 저장되어 충돌해도 정상으로 처리한다")
        void joinWhenRecordSavedConcurrently_succeeds() {
            // given
            givenJoinableRoom(true);
            givenReserve(true);
            givenTicketIssued();
            givenLiveRoom();
            given(roomParticipantRepository.existsByRoomIdAndMemberId(ROOM_ID, MEMBER_ID))
                    .willReturn(false, true);
            given(roomParticipantRepository.save(any(RoomParticipant.class)))
                    .willThrow(new DataIntegrityViolationException("duplicate"));

            // when & then
            assertThatCode(() -> service.joinRoom(ROOM_ID, MEMBER_ID, new JoinWatchingRoomRequest(null)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("중복이 아닌 무결성 오류는 삼키지 않고 다시 던진다")
        void joinWhenRecordFailsForOtherReason_propagates() {
            // given
            givenJoinableRoom(true);
            givenReserve(true);
            givenTicketIssued();
            givenLiveRoom();
            given(roomParticipantRepository.save(any(RoomParticipant.class)))
                    .willThrow(new DataIntegrityViolationException("fk violation"));

            // when & then
            assertThatThrownBy(() -> service.joinRoom(ROOM_ID, MEMBER_ID, new JoinWatchingRoomRequest(null)))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        // === given ===

        private void givenJoinableRoom(boolean isPublic) {
            given(watchingRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
            given(room.isJoinable(NOW)).willReturn(true);
            given(room.getIsPublic()).willReturn(isPublic);
        }

        private void givenReserve(boolean reserved) {
            given(room.getMaxParticipants()).willReturn(MAX_PARTICIPANTS);
            given(presenceStore.reserve(ROOM_ID, MEMBER_ID, MAX_PARTICIPANTS, WebSocketTicketStore.TTL))
                    .willReturn(reserved);
        }

        private void givenTicketIssued() {
            given(ticketStore.issue(eq(MEMBER_ID), eq(ROOM_ID), anyString())).willReturn(TICKET);
        }

        private void givenLiveRoom() {
            given(room.isLiveAt(NOW)).willReturn(true);
            given(room.getId()).willReturn(ROOM_ID);
            given(memberRepository.getReferenceById(MEMBER_ID)).willReturn(member);
        }
    }

    @Nested
    @DisplayName("시청방 나가기")
    class LeaveRoom {

        @Test
        @DisplayName("없는 방에서 나가면 404 예외이고, 접속 상태는 건드리지 않는다")
        void leaveUnknownRoom_isRejected() {
            // given
            given(watchingRoomRepository.existsById(ROOM_ID)).willReturn(false);

            // when & then
            assertThatThrownBy(() -> service.leaveRoom(ROOM_ID, MEMBER_ID, TAB_ID))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(WatchingRoomErrorCode.WATCHING_ROOM_NOT_FOUND));
            then(presenceStore).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("Redis 장애로 접속 상태 정리에 실패해도 나가기는 성공으로 처리한다")
        void leaveWhenStoreFails_stillSucceeds() {
            // given
            given(watchingRoomRepository.existsById(ROOM_ID)).willReturn(true);
            given(presenceStore.disconnect(ROOM_ID, MEMBER_ID, TAB_ID))
                    .willThrow(new IllegalStateException("redis down"));

            // when & then
            assertThatCode(() -> service.leaveRoom(ROOM_ID, MEMBER_ID, TAB_ID))
                    .doesNotThrowAnyException();
        }
    }
}