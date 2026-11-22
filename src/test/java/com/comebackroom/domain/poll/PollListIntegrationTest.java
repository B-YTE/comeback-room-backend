package com.comebackroom.domain.poll;

import com.comebackroom.TestcontainersConfiguration;
import com.comebackroom.domain.member.entity.Member;
import com.comebackroom.domain.member.repository.MemberRepository;
import com.comebackroom.domain.poll.entity.Poll;
import com.comebackroom.domain.poll.entity.PollVote;
import com.comebackroom.domain.poll.repository.PollOptionRepository;
import com.comebackroom.domain.poll.repository.PollRepository;
import com.comebackroom.domain.poll.repository.PollVoteRepository;
import com.comebackroom.domain.watchingroom.entity.RoomParticipant;
import com.comebackroom.domain.watchingroom.entity.WatchingRoom;
import com.comebackroom.domain.watchingroom.repository.RoomParticipantRepository;
import com.comebackroom.domain.watchingroom.repository.WatchingRoomRepository;
import com.comebackroom.global.security.jwt.JwtTokenProvider;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.SQL=debug"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@DisplayName("투표 목록 조회 통합 테스트")
class PollListIntegrationTest {

    private static final String GET_POLLS_URL = "/api/v1/watching-rooms/{roomId}/polls";

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private MemberRepository memberRepository;
    @Autowired private WatchingRoomRepository watchingRoomRepository;
    @Autowired private RoomParticipantRepository roomParticipantRepository;
    @Autowired private PollRepository pollRepository;
    @Autowired private PollOptionRepository pollOptionRepository;
    @Autowired private PollVoteRepository pollVoteRepository;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private Clock clock;

    private Member host;
    private Member voter;
    private WatchingRoom room;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now(clock);
        host = memberRepository.save(
                Member.createLocalMember("host123", "{bcrypt}encoded-password", "host@example.com"));
        voter = memberRepository.save(
                Member.createLocalMember("voter123", "{bcrypt}encoded-password", "voter@example.com"));
        room = watchingRoomRepository.save(createRoom(host, true));
    }

    @AfterEach
    void tearDown() {
        // FK 반대 순서로 지운다: 득표 → 선택지 → 투표 → 참여 기록 → 시청방 → 회원
        pollVoteRepository.deleteAllInBatch();
        pollOptionRepository.deleteAllInBatch();
        pollRepository.deleteAllInBatch();
        roomParticipantRepository.deleteAllInBatch();
        watchingRoomRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("투표 3개, 선택지 각 4개를 조회할 때 실행되는 SQL은 3개다")
    void executesThreeQueries() throws Exception {
        // given
        for (int i = 1; i <= 3; i++) {
            Poll poll = pollRepository.save(createPoll("질문" + i, now, 10, "A", "B", "C", "D"));
            vote(poll, voter, 0);
        }
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        // when
        mockMvc.perform(get(GET_POLLS_URL, room.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(voter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.polls.length()").value(3));

        // then
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("같은 투표라도 요청자마다 isSelected가 다르게 나온다")
    void isSelectedDependsOnRequester() throws Exception {
        // given
        Poll poll = pollRepository.save(createPoll("엔딩포즈 제일 잘 한 멤버는?", now, 10, "원영", "유진"));
        vote(poll, voter, 0);
        vote(poll, host, 1);

        // when & then: voter가 조회
        mockMvc.perform(get(GET_POLLS_URL, room.getId())
                    .header(HttpHeaders.AUTHORIZATION, bearerToken(voter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("POLL_LIST_RETRIEVED"))
                .andExpect(jsonPath("$.data.polls[0].totalVoteCount").value(2))
                .andExpect(jsonPath("$.data.polls[0].pollOptions[0].voteCount").value(1))
                .andExpect(jsonPath("$.data.polls[0].pollOptions[0].isSelected").value(true))
                .andExpect(jsonPath("$.data.polls[0].pollOptions[1].isSelected").value(false));

        // when & then: host가 조회
        mockMvc.perform(get(GET_POLLS_URL, room.getId())
                    .header(HttpHeaders.AUTHORIZATION, bearerToken(host)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.polls[0].pollOptions[0].isSelected").value(false))
                .andExpect(jsonPath("$.data.polls[0].pollOptions[1].isSelected").value(true));
    }

    @Test
    @DisplayName("진행 중 투표를 마감 임박순으로, 이어서 종료 투표를 최근 마감순으로 반환한다")
    void sortsByStatusAndExpiresAt() throws Exception {
        // given
        pollRepository.save(createPoll("오래전 마감", now.minusMinutes(30), 10, "A", "B"));
        pollRepository.save(createPoll("최근 마감", now.minusMinutes(15), 10, "A", "B"));
        pollRepository.save(createPoll("나중 마감", now, 30, "A", "B"));
        pollRepository.save(createPoll("곧 마감", now, 5, "A", "B"));

        // when & then
        mockMvc.perform(get(GET_POLLS_URL, room.getId())
                    .header(HttpHeaders.AUTHORIZATION, bearerToken(voter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.polls[*].question")
                        .value(contains("곧 마감", "나중 마감", "최근 마감", "오래전 마감")))
                .andExpect(jsonPath("$.data.polls[*].status")
                        .value(contains("ONGOING", "ONGOING", "ENDED", "ENDED")));
    }

    @Test
    @DisplayName("비공개방에 참여 기록이 있으면 조회할 수 있다")
    void getPollsWhenParticipantOfPrivateRoom() throws Exception {
        // given
        WatchingRoom privateRoom = watchingRoomRepository.save(createRoom(host, false));
        roomParticipantRepository.save(RoomParticipant.builder()
                .member(voter)
                .room(privateRoom)
                .build());

        // when & then
        mockMvc.perform(get(GET_POLLS_URL, privateRoom.getId())
                    .header(HttpHeaders.AUTHORIZATION, bearerToken(voter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("POLL_LIST_RETRIEVED"))
                .andExpect(jsonPath("$.data.polls").isEmpty());
    }

    @Test
    @DisplayName("비공개방이고 참여 기록이 없으면 403를 반환한다")
    void failWhenNotParticipantOfPrivateRoom() throws Exception {
        // given
        WatchingRoom privateRoom = watchingRoomRepository.save(createRoom(host, false));

        // when & then
        mockMvc.perform(get(GET_POLLS_URL, privateRoom.getId())
                    .header(HttpHeaders.AUTHORIZATION, bearerToken(voter)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_WATCHING_ROOM_PARTICIPANT"))
                .andExpect(jsonPath("$.message").value("비공개 시청방은 참여자만 조회할 수 있습니다."));
    }

    @Test
    @DisplayName("비공개방의 방장은 참여 기록이 없어도 조회할 수 있다")
    void getPollsWhenHostOfPrivateRoom() throws Exception {
        // given
        WatchingRoom privateRoom = watchingRoomRepository.save(createRoom(host, false));

        // when & then
        mockMvc.perform(get(GET_POLLS_URL, privateRoom.getId())
                    .header(HttpHeaders.AUTHORIZATION, bearerToken(host)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("존재하지 않는 시청방이면 404를 반환한다")
    void failWhenRoomNotFound() throws Exception {
        mockMvc.perform(get(GET_POLLS_URL, 999_999L)
                    .header(HttpHeaders.AUTHORIZATION, bearerToken(voter)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WATCHING_ROOM_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("존재하지 않는 시청방입니다."));
    }

    @Test
    @DisplayName("토큰 없이 요청하면 401을 반환한다")
    void failWithoutToken() throws Exception {
        mockMvc.perform(get(GET_POLLS_URL, room.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."));
    }


    private Poll createPoll(String question, LocalDateTime createTime, int durationMinutes, String... contents) {
        return Poll.create(room, question, List.of(contents), createTime, durationMinutes);
    }

    private void vote(Poll poll, Member member, int optionIndex) {
        pollVoteRepository.save(PollVote.builder()
                .poll(poll)
                .member(member)
                .pollOption(poll.getPollOptionList().get(optionIndex))
                .build());
    }

    private String bearerToken(Member member) {
        return "Bearer " + jwtTokenProvider.createAccessToken(member.getId());
    }

    private WatchingRoom createRoom(Member host, boolean isPublic) {
        return WatchingRoom.builder()
                .host(host)
                .title("컴백 같이 보기")
                .videoUrl("https://www.youtube.com/watch?v=example")
                .videoTitle("컴백 뮤직비디오")
                .videoThumbnailUrl("https://img.youtube.com/vi/example/0.jpg")
                .videoDurationSeconds(240)
                .maxParticipants(100)
                .isPublic(isPublic)
                .privateCode(isPublic ? null : "102030") // 비공개방은 입장 코드가 꼭 있어야 한다 (WatchingRoom 생성자 검증)
                .startAt(LocalDateTime.of(2026, 11, 20, 20, 0))
                .build();
    }
}
