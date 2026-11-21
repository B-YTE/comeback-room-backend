package com.comebackroom.domain.poll.service;

import com.comebackroom.domain.member.entity.Member;
import com.comebackroom.domain.poll.dto.request.CreatePollRequest;
import com.comebackroom.domain.poll.dto.request.CreatePollRequest.PollOptionRequest;
import com.comebackroom.domain.poll.dto.response.PollListResponse;
import com.comebackroom.domain.poll.dto.response.PollResponse;
import com.comebackroom.domain.poll.entity.Poll;
import com.comebackroom.domain.poll.enums.PollStatus;
import com.comebackroom.domain.poll.exception.PollErrorCode;
import com.comebackroom.domain.poll.repository.PollRepository;
import com.comebackroom.domain.poll.repository.PollVoteRepository;
import com.comebackroom.domain.watchingroom.entity.WatchingRoom;
import com.comebackroom.domain.watchingroom.exception.WatchingRoomErrorCode;
import com.comebackroom.domain.watchingroom.repository.RoomParticipantRepository;
import com.comebackroom.domain.watchingroom.repository.WatchingRoomRepository;
import com.comebackroom.global.config.TimeZoneConfig;
import com.comebackroom.global.exception.BusinessException;
import com.comebackroom.global.exception.GlobalErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("투표 서비스")
class PollServiceTest {

    private static final Long ROOM_ID = 3L;
    private static final Long HOST_ID = 7L;
    private static final Long OTHER_MEMBER_ID = 8L;
    private static final String QUESTION = "엔딩포즈 제일 잘 한 멤버는?";
    private static final int DURATION_MINUTES = 10;
    private static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");

    @Mock
    private PollRepository pollRepository;

    @Mock
    private PollVoteRepository pollVoteRepository;

    @Mock
    private WatchingRoomRepository watchingRoomRepository;

    @Mock
    private RoomParticipantRepository roomParticipantRepository;

    private PollService pollService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(NOW, TimeZoneConfig.APPLICATION_ZONE_ID);
        pollService = new PollService(pollRepository, pollVoteRepository, watchingRoomRepository,
                roomParticipantRepository, fixedClock);
    }

    @Test
    @DisplayName("방장이 진행 중인 방에서 투표를 생성한다")
    void createPoll() {
        // given
        WatchingRoom room = createRoom(HOST_ID);
        room.start();
        given(watchingRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));

        // when
        PollResponse response = pollService.createPoll(
                ROOM_ID, HOST_ID, createRequest("원영", "유진", "레이", "리즈"));

        // then
        ArgumentCaptor<Poll> pollCaptor = ArgumentCaptor.forClass(Poll.class);
        verify(pollRepository).save(pollCaptor.capture());
        Poll savedPoll = pollCaptor.getValue();

        assertAll(
                () -> assertThat(response.question()).isEqualTo(QUESTION),
                () -> assertThat(response.status()).isEqualTo(PollStatus.ONGOING),
                () -> assertThat(response.totalVoteCount()).isZero(),
                () -> assertThat(response.createdAt())
                        .isEqualTo(LocalDateTime.of(2026, 11, 19, 21, 0)),
                () -> assertThat(response.expiresAt())
                        .isEqualTo(LocalDateTime.of(2026, 11, 19, 21, 10)),
                () -> assertThat(Duration.between(savedPoll.getCreatedAt(), savedPoll.getExpiresAt()))
                        .isEqualTo(Duration.ofMinutes(DURATION_MINUTES)),
                () -> assertThat(response.pollOptions())
                        .extracting(PollResponse.PollOptionResponse::content)
                        .containsExactly("원영", "유진", "레이", "리즈"),
                () -> assertThat(response.pollOptions()).allSatisfy(option -> {
                    assertThat(option.voteCount()).isZero();
                    assertThat(option.isSelected()).isFalse();
                }),
                () -> assertThat(savedPoll.getRoom()).isSameAs(room),
                () -> assertThat(savedPoll.getPollOptionList())
                        .allSatisfy(option -> assertThat(option.getPoll()).isSameAs(savedPoll))
        );
    }

    @Test
    @DisplayName("시작 전(SCHEDULED) 방에서도 투표를 생성한다")
    void createPollInScheduledRoom() {
        WatchingRoom room = createRoom(HOST_ID);
        given(watchingRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));

        pollService.createPoll(ROOM_ID, HOST_ID, createRequest("원영", "유진"));

        verify(pollRepository).save(any(Poll.class));
    }

    @Test
    @DisplayName("회원 ID가 Long 캐시 범위(-128~127)를 벗어나도 방장을 인식한다")
    void recognizeHostWithLargeId() {
        WatchingRoom room = createRoom(Long.valueOf(1000L));
        room.start();
        given(watchingRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));

        pollService.createPoll(ROOM_ID, Long.valueOf(1000L), createRequest("원영", "유진"));

        verify(pollRepository).save(any(Poll.class));
    }

    @Test
    @DisplayName("시청방이 없으면 WATCHING_ROOM_NOT_FOUND 예외가 발생한다")
    void failWhenRoomNotFound() {
        given(watchingRoomRepository.findById(ROOM_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> pollService.createPoll(ROOM_ID, HOST_ID, createRequest("원영", "유진")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", WatchingRoomErrorCode.WATCHING_ROOM_NOT_FOUND);

        verify(pollRepository, never()).save(any());
    }

    @Test
    @DisplayName("방장이 아니면 POLL_CREATE_FORBIDDEN 예외가 발생한다")
    void failWhenNotHost() {
        WatchingRoom room = createRoom(HOST_ID);
        room.start();
        given(watchingRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));

        assertThatThrownBy(() -> pollService.createPoll(ROOM_ID, OTHER_MEMBER_ID, createRequest("원영", "유진")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", PollErrorCode.POLL_CREATE_FORBIDDEN);

        verify(pollRepository, never()).save(any());
    }

    @Test
    @DisplayName("종료된 방이면 WATCHING_ROOM_ALREADY_ENDED 예외가 발생한다")
    void failWhenRoomEnded() {
        WatchingRoom room = createRoom(HOST_ID);
        room.start();
        room.end();
        given(watchingRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));

        assertThatThrownBy(() -> pollService.createPoll(ROOM_ID, HOST_ID, createRequest("원영", "유진")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", PollErrorCode.WATCHING_ROOM_ALREADY_ENDED);

        verify(pollRepository, never()).save(any());
    }

    @Test
    @DisplayName("공백을 제거한 선택지가 중복되면 INVALID_INPUT_VALUE 예외가 발생한다")
    void failWhenDuplicateOption() {
        WatchingRoom room = createRoom(HOST_ID);
        room.start();
        given(watchingRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));

        assertThatThrownBy(() -> pollService.createPoll(ROOM_ID, HOST_ID, createRequest("원영", "유진", "원영 ")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", GlobalErrorCode.INVALID_INPUT_VALUE);

        verify(pollRepository, never()).save(any());
    }

    @Test
    @DisplayName("투표가 없으면 득표 집계 쿼리를 호출하지 않고 빈 목록을 반환한다")
    void skipVoteCountWhenNoPolls() {
        // given
        given(watchingRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(createRoom(HOST_ID)));
        given(pollRepository.findAllWithOptionsByRoomId(ROOM_ID)).willReturn(List.of());

        // when
        PollListResponse response = pollService.getPolls(ROOM_ID, OTHER_MEMBER_ID);

        // then
        assertThat(response.polls()).isEmpty();
        verify(pollVoteRepository, never()).countByPollIds(any(), any());
    }

    @Test
    @DisplayName("공개방이면 참여 기록을 조회하지 않는다")
    void skipParticipantCheckInPublicRoom() {
        // given
        given(watchingRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(createRoom(HOST_ID)));
        given(pollRepository.findAllWithOptionsByRoomId(ROOM_ID)).willReturn(List.of());

        // when
        pollService.getPolls(ROOM_ID, OTHER_MEMBER_ID);

        // then
        verify(roomParticipantRepository, never()).existsByRoomIdAndMemberId(any(), any());
    }

    @Test
    @DisplayName("비공개방의 방장이면 참여 기록을 조회하지 않는다")
    void skipParticipantCheckForHostOfPrivateRoom() {
        // given
        given(watchingRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(createRoom(HOST_ID, false)));
        given(pollRepository.findAllWithOptionsByRoomId(ROOM_ID)).willReturn(List.of());

        // when
        pollService.getPolls(ROOM_ID, HOST_ID);

        // then
        verify(roomParticipantRepository, never()).existsByRoomIdAndMemberId(any(), any());
    }

    @Test
    @DisplayName("비공개방에 참여 기록이 없으면 403 예외가 나고 투표를 조회하지 않는다")
    void failWhenNotParticipantOfPrivateRoom() {
        // given
        given(watchingRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(createRoom(HOST_ID, false)));
        given(roomParticipantRepository.existsByRoomIdAndMemberId(ROOM_ID, OTHER_MEMBER_ID)).willReturn(false);

        // when
        assertThatThrownBy(() -> pollService.getPolls(ROOM_ID, OTHER_MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", PollErrorCode.NOT_WATCHING_ROOM_PARTICIPANT);

        // then
        verify(pollRepository, never()).findAllWithOptionsByRoomId(any());
    }

    @Test
    @DisplayName("비공개방 참여자는 투표를 조회할 수 있다")
    void getPollsWhenParticipantOfPrivateRoom() {
        // given
        given(watchingRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(createRoom(HOST_ID, false)));
        given(roomParticipantRepository.existsByRoomIdAndMemberId(ROOM_ID, OTHER_MEMBER_ID)).willReturn(true);
        given(pollRepository.findAllWithOptionsByRoomId(ROOM_ID)).willReturn(List.of());

        // when
        PollListResponse response = pollService.getPolls(ROOM_ID, OTHER_MEMBER_ID);

        // then
        assertThat(response.polls()).isEmpty();
    }

    private WatchingRoom createRoom(Long hostId) {
        return createRoom(hostId, true);
    }

    private WatchingRoom createRoom(Long hostId, boolean isPublic) {
        Member host = Member.createLocalMember("host123", "{bcrypt}encoded-password", "host@example.com");
        ReflectionTestUtils.setField(host, "id", hostId);

        WatchingRoom room = WatchingRoom.builder()
                .host(host)
                .title("컴백 같이 보기")
                .videoUrl("https://www.youtube.com/watch?v=example")
                .videoTitle("컴백 뮤직비디오")
                .videoThumbnailUrl("https://img.youtube.com/vi/example/0.jpg")
                .videoDurationSeconds(240)
                .maxParticipants(100)
                .isPublic(isPublic)
                .privateCode(isPublic ? null : "102030")
                .startAt(LocalDateTime.of(2026, 11, 19, 20, 0))
                .build();

        ReflectionTestUtils.setField(room, "id", ROOM_ID);
        return room;
    }

    private CreatePollRequest createRequest(String... content) {
        List<PollOptionRequest> pollOptions = Arrays.stream(content)
                .map(PollOptionRequest::new)
                .toList();
        return new CreatePollRequest(QUESTION, pollOptions, DURATION_MINUTES);
    }
}
