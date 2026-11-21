package com.comebackroom.domain.poll.repository;

import com.comebackroom.TestcontainersConfiguration;
import com.comebackroom.domain.member.entity.Member;
import com.comebackroom.domain.member.repository.MemberRepository;
import com.comebackroom.domain.poll.dto.query.PollOptionVoteCount;
import com.comebackroom.domain.poll.entity.Poll;
import com.comebackroom.domain.poll.entity.PollOption;
import com.comebackroom.domain.poll.entity.PollVote;
import com.comebackroom.domain.watchingroom.entity.WatchingRoom;
import com.comebackroom.domain.watchingroom.repository.WatchingRoomRepository;
import com.comebackroom.global.config.JpaConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@Import({TestcontainersConfiguration.class, JpaConfig.class})
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("투표 목록 조회 쿼리")
class PollListQueryTest {

    private static final LocalDateTime CREATE_TIME = LocalDateTime.of(2026, 11, 20, 18, 0);

    @Autowired private PollRepository pollRepository;
    @Autowired private PollVoteRepository pollVoteRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private WatchingRoomRepository watchingRoomRepository;
    @Autowired private EntityManager entityManager;

    private Member host;
    private Member voter;
    private WatchingRoom room;

    @BeforeEach
    void setUp() {
        host = memberRepository.save(
                Member.createLocalMember("host123", "{bcrypt}encoded-password", "host@example.com"));
        voter = memberRepository.save(
                Member.createLocalMember("voter123", "{bcrypt}encoded-password", "voter@example.com"));
        room = watchingRoomRepository.save(createRoom(host));
    }

    @Test
    @DisplayName("시청방의 투표를 선택지와 함께 한 번에 조회하고, 선택지는 생성 순서대로 담긴다")
    void findAllWithOptionsByRoomId() {
        // given
        Poll first = pollRepository.save(createPoll(room, "원영", "유진"));
        Poll second = pollRepository.save(createPoll(room, "1절", "2절", "3절"));
        entityManager.flush();
        entityManager.clear();

        // when
        List<Poll> polls = pollRepository.findAllWithOptionsByRoomId(room.getId());

        // then
        assertThat(polls).extracting(Poll::getId)
                .containsExactly(first.getId(), second.getId());
        assertThat(polls.get(0).getPollOptionList()).extracting(PollOption::getContent)
                .containsExactly("원영", "유진");
        assertThat(polls.get(1).getPollOptionList()).extracting(PollOption::getContent)
                .containsExactly("1절", "2절", "3절");
        assertThat(entityManager.getEntityManagerFactory().getPersistenceUnitUtil()
                .isLoaded(polls.get(0), "pollOptionList")).isTrue();
    }

    @Test
    @DisplayName("다른 시청방의 투표는 조회되지 않는다")
    void excludePollsOfOtherRoom() {
        // given
        WatchingRoom otherRoom = watchingRoomRepository.save(createRoom(host));
        Poll myRoomPoll = pollRepository.save(createPoll(room, "원영", "유진"));
        pollRepository.save(createPoll(otherRoom, "토끼", "다람쥐"));
        entityManager.flush();
        entityManager.clear();

        // when
        List<Poll> polls = pollRepository.findAllWithOptionsByRoomId(room.getId());

        // then
        assertThat(polls).extracting(Poll::getId)
                .containsExactly(myRoomPoll.getId());
    }

    @Test
    @DisplayName("선택지별 득표 수와 요청자가 고른 선택지를 집계한다")
    void countByPollIds() {
        // given
        Member other = memberRepository.save(
                Member.createLocalMember("other123", "{bcrypt}encoded-password", "other@example.com"));
        Poll poll = pollRepository.save(createPoll(room, "원영", "유진", "레이"));
        PollOption wonyoung = poll.getPollOptionList().get(0);
        PollOption yujin = poll.getPollOptionList().get(1);

        vote(poll, voter, wonyoung);
        vote(poll, other, wonyoung);
        vote(poll, host, yujin);
        entityManager.flush();
        entityManager.clear();

        // when
        List<PollOptionVoteCount> counts =
                pollVoteRepository.countByPollIds(List.of(poll.getId()), voter.getId());

        // then: 레이는 0표라 결과에 행이 없다
        assertThat(counts)
                .extracting(PollOptionVoteCount::pollOptionId,
                        PollOptionVoteCount::voteCount,
                        PollOptionVoteCount::isSelected)
                .containsExactlyInAnyOrder(
                        tuple(wonyoung.getId(), 2L, true),
                        tuple(yujin.getId(), 1L, false));
    }

    @Test
    @DisplayName("같은 데이터라도 요청자가 바뀌면 isSelected가 달라진다")
    void isSelectedDependsOnRequester() {
        // given
        Poll poll = pollRepository.save(createPoll(room, "원영", "유진"));
        PollOption wonyoung = poll.getPollOptionList().get(0);
        PollOption yujin = poll.getPollOptionList().get(1);

        vote(poll, voter, wonyoung);
        vote(poll, host, yujin);
        entityManager.flush();
        entityManager.clear();

        // when: 같은 투표를 요청자만 바꿔서 두 번 조회
        List<PollOptionVoteCount> voterView =
                pollVoteRepository.countByPollIds(List.of(poll.getId()), voter.getId());
        List<PollOptionVoteCount> hostView =
                pollVoteRepository.countByPollIds(List.of(poll.getId()), host.getId());

        // then
        assertThat(voterView)
                .extracting(PollOptionVoteCount::pollOptionId, PollOptionVoteCount::isSelected)
                .containsExactlyInAnyOrder(tuple(wonyoung.getId(), true), tuple(yujin.getId(), false));
        assertThat(hostView)
                .extracting(PollOptionVoteCount::pollOptionId, PollOptionVoteCount::isSelected)
                .containsExactlyInAnyOrder(tuple(wonyoung.getId(), false), tuple(yujin.getId(), true));
    }

    private Poll createPoll(WatchingRoom room, String... contents) {
        return Poll.create(room, "엔딩포즈 제일 잘 한 멤버는?", List.of(contents), CREATE_TIME, 10);
    }

    private void vote(Poll poll, Member member, PollOption option) {
        pollVoteRepository.save(PollVote.builder()
                .poll(poll)
                .member(member)
                .pollOption(option)
                .build());
    }

    private WatchingRoom createRoom(Member host) {
        return WatchingRoom.builder()
                .host(host)
                .title("컴백 같이 보기")
                .videoUrl("https://www.youtube.com/watch?v=example")
                .videoTitle("컴백 뮤직비디오")
                .videoThumbnailUrl("https://img.youtube.com/vi/example/0.jpg")
                .videoDurationSeconds(240)
                .maxParticipants(100)
                .isPublic(true)
                .privateCode(null)
                .startAt(LocalDateTime.of(2026, 11, 20, 20, 0))
                .build();
    }
}
