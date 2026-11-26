package com.comebackroom.domain.poll;

import com.comebackroom.TestcontainersConfiguration;
import com.comebackroom.domain.member.entity.Member;
import com.comebackroom.domain.member.repository.MemberRepository;
import com.comebackroom.domain.poll.entity.Poll;
import com.comebackroom.domain.poll.entity.PollVote;
import com.comebackroom.domain.poll.repository.PollOptionRepository;
import com.comebackroom.domain.poll.repository.PollRepository;
import com.comebackroom.domain.poll.repository.PollVoteRepository;
import com.comebackroom.domain.watchingroom.entity.WatchingRoom;
import com.comebackroom.domain.watchingroom.repository.RoomParticipantRepository;
import com.comebackroom.domain.watchingroom.repository.WatchingRoomRepository;
import com.comebackroom.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.SQL=debug"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@DisplayName("투표 삭제 통합 테스트")
class PollDeleteIntegrationTest {

    private static final String DELETE_POLLS_URL = "/api/v1/polls/{pollId}";

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private MemberRepository memberRepository;
    @Autowired private WatchingRoomRepository watchingRoomRepository;
    @Autowired private RoomParticipantRepository roomParticipantRepository;
    @Autowired private PollRepository pollRepository;
    @Autowired private PollOptionRepository pollOptionRepository;
    @Autowired private PollVoteRepository pollVoteRepository;
    @Autowired private Clock clock;

    private Member host;
    private Member voter;
    private WatchingRoom room;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now(clock);
        host = memberRepository.save(
                Member.createLocalMember("host1223", "{bcrypt}encoded-password", "host@example.com"));
        voter = memberRepository.save(
                Member.createLocalMember("voter123", "{bcrypt}encoded-password", "voter@example.com"));
        room = watchingRoomRepository.save(createRoom(host, true));
    }

    @AfterEach
    void tearDown() {
        pollVoteRepository.deleteAllInBatch();
        pollOptionRepository.deleteAllInBatch();
        pollRepository.deleteAllInBatch();
        roomParticipantRepository.deleteAllInBatch();
        watchingRoomRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("방장이 투표를 삭제하면 선택지와 득표가 함께 삭제되고, 다른 투표는 남는다")
    void deletePoll() throws Exception {
        // given
        Poll target = pollRepository.save(createPoll("삭제할 투표", now, "A", "B"));
        vote(target, voter, 0);
        vote(target, host, 1);
        Poll other = pollRepository.save(createPoll("남을 투표", now, "C", "D"));
        vote(other, voter, 0);

        // when & then
        mockMvc.perform(delete(DELETE_POLLS_URL, target.getId())
                    .header(HttpHeaders.AUTHORIZATION, bearerToken(host)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("POLL_DELETED"))
                .andExpect(jsonPath("$.data").doesNotExist());

        // then
        assertThat(pollRepository.existsById(target.getId())).isFalse();
        assertThat(pollRepository.existsById(other.getId())).isTrue();
        assertThat(pollOptionRepository.count()).isEqualTo(2); // other의 C, D만
        assertThat(pollVoteRepository.count()).isEqualTo(1); // other의 1표만
    }

    @Test
    @DisplayName("방장이 아닌 사람이 투표를 삭제하면 403을 반환한다")
    void failWhenNotHost() throws Exception {
        // given
        Poll target = pollRepository.save(createPoll("삭제할 투표", now, "A", "B"));
        vote(target, voter, 0);

        // when & then
        mockMvc.perform(delete(DELETE_POLLS_URL, target.getId())
                    .header(HttpHeaders.AUTHORIZATION, bearerToken(voter)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("POLL_DELETE_FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("방장만 투표를 삭제할 수 있습니다."));
    }

    @Test
    @DisplayName("투표가 없다면 404를 반환한다")
    void failWhenPollNotFound() throws Exception {
        mockMvc.perform(delete(DELETE_POLLS_URL, 999_999L)
                    .header(HttpHeaders.AUTHORIZATION, bearerToken(host)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POLL_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("존재하지 않는 투표입니다."));
    }

    @Test
    @DisplayName("종료된 시청방의 투표를 삭제한다면 409를 반환하고 투표를 삭제하지 않는다")
    void failWhenRoomEnded() throws Exception {
        // given
        Poll target = pollRepository.save(createPoll("삭제할 투표", now, "A", "B"));
        vote(target, voter, 0);
        room.start();
        room.end();
        watchingRoomRepository.save(room);

        // when & then
        mockMvc.perform(delete(DELETE_POLLS_URL, target.getId())
                    .header(HttpHeaders.AUTHORIZATION, bearerToken(host)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WATCHING_ROOM_ALREADY_ENDED"))
                .andExpect(jsonPath("$.message").value("이미 종료된 시청방입니다."));

        assertPollDataUnchanged(1, 2, 1);
    }

    @Test
    @DisplayName("종료된 투표를 삭제한다면 409를 반환하고 투표를 삭제하지 않는다")
    void failWhenPollEnded() throws Exception {
        // given
        Poll target = pollRepository.save(createPoll("삭제할 투표", now.minusMinutes(30), "A", "B"));

        // when & then
        mockMvc.perform(delete(DELETE_POLLS_URL, target.getId())
                    .header(HttpHeaders.AUTHORIZATION, bearerToken(host)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POLL_ALREADY_ENDED"))
                .andExpect(jsonPath("$.message").value("이미 마감된 투표입니다."));

        assertPollDataUnchanged(1, 2, 0);
    }

    @Test
    @DisplayName("토큰 없이 요청하면 401을 반환하고 투표를 삭제하지 않는다")
    void failWithoutToken() throws Exception {
        // given
        Poll target = pollRepository.save(createPoll("삭제할 투표", now, "A", "B"));

        mockMvc.perform(delete(DELETE_POLLS_URL, target.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."));

        assertThat(pollRepository.count()).isOne();
    }

    private Poll createPoll(String question, LocalDateTime createTime, String... contents) {
        return Poll.create(room, question, List.of(contents), createTime, 10);
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

    private void assertPollDataUnchanged(long polls, long options, long votes) {
        assertThat(pollRepository.count()).isEqualTo(polls);
        assertThat(pollOptionRepository.count()).isEqualTo(options);
        assertThat(pollVoteRepository.count()).isEqualTo(votes);
    }
}
