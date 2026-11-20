package com.comebackroom.domain.poll;

import com.comebackroom.TestcontainersConfiguration;
import com.comebackroom.domain.member.entity.Member;
import com.comebackroom.domain.member.repository.MemberRepository;
import com.comebackroom.domain.poll.entity.Poll;
import com.comebackroom.domain.poll.entity.PollOption;
import com.comebackroom.domain.poll.enums.PollStatus;
import com.comebackroom.domain.poll.repository.PollOptionRepository;
import com.comebackroom.domain.poll.repository.PollRepository;
import com.comebackroom.domain.watchingroom.entity.WatchingRoom;
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
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@DisplayName("투표 생성 통합 테스트")
class PollCreateIntegrationTest {

    private static final String CREATE_POL_URL = "/api/v1/watching-rooms/{roomId}/polls";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private WatchingRoomRepository watchingRoomRepository;

    @Autowired
    private PollRepository pollRepository;

    @Autowired
    private PollOptionRepository pollOptionRepository;

    private Member host;
    private WatchingRoom room;

    @BeforeEach
    void setUp() {
        host = memberRepository.save(
                Member.createLocalMember("host123", "{bcrypt}encoded-password", "host@example.com"));
        room = createRoom(host);
        room.start();
        watchingRoomRepository.save(room);
    }

    @AfterEach
    void tearDown() {
        // FK가 걸린 순서의 반대로 지운다: 선택지 → 투표 → 시청방 → 회원
        pollOptionRepository.deleteAllInBatch();
        pollRepository.deleteAllInBatch();
        watchingRoomRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("방장이 투표를 만들면 투표와 선택지가 요청 순서대로 저장되고, 마감 시각은 생성 시각 10분 뒤다")
    void createPoll() throws Exception {
        // given
        String accessToken = jwtTokenProvider.createAccessToken(host.getId());

        // when
        mockMvc.perform(post(CREATE_POL_URL, room.getId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody("원영", "유진", "레이")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("POLL_CREATED"));

        // then: DB에 실제로 저장된 값 확인
        List<Poll> pollList = pollRepository.findAll();
        List<PollOption> pollOptionList = pollOptionRepository.findAll(Sort.by("id"));

        assertThat(pollList).hasSize(1);
        Poll savedPoll = pollList.get(0);

        assertAll(
                () -> assertThat(savedPoll.getStatus()).isEqualTo(PollStatus.ONGOING),
                () -> assertThat(savedPoll.getCreatedAt()).isNotNull(),
                () -> assertThat(savedPoll.getExpiresAt())
                        .isCloseTo(savedPoll.getCreatedAt().plusMinutes(10), within(1, ChronoUnit.SECONDS)),
                () -> assertThat(pollOptionList)
                        .extracting(PollOption::getContent)
                        .containsExactly("원영", "유진", "레이")
        );
    }

    @Test
    @DisplayName("토큰 없이 요청하면 401을 반환하고 투표를 저장하지 않는다")
    void failWithoutToken() throws Exception {
        mockMvc.perform(post(CREATE_POL_URL, room.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody("원영", "유진")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."));

        assertThat(pollRepository.count()).isZero();
    }

    @Test
    @DisplayName("방장이 아닌 회원이 요청하면 403을 반환하고 투표를 저장하지 않는다")
    void failWhenNotHost() throws Exception {
        Member otherMember = memberRepository.save(
                Member.createLocalMember("otherMember", "{bcrypt}encoded-password", "other@example.com"));
        String accessToken = jwtTokenProvider.createAccessToken(otherMember.getId());

        mockMvc.perform(post(CREATE_POL_URL, room.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(otherMember))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody("원영", "유진")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("POLL_CREATE_FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("방장만 투표를 생성할 수 있습니다."));

        assertThat(pollRepository.count()).isZero();
    }

    @Test
    @DisplayName("종료된 방에서 요청하면 409를 반환하고 투표를 저장하지 않는다")
    void failWhenRoomEnded() throws Exception {
        // given: setUp()의 LIVE 방을 종료하고 다시 저장한다 (id가 있으니 UPDATE)
        room.end();
        watchingRoomRepository.save(room);

        // when & then
        mockMvc.perform(post(CREATE_POL_URL, room.getId())
                    .header(HttpHeaders.AUTHORIZATION, bearerToken(host))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody("원영", "유진")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WATCHING_ROOM_ALREADY_ENDED"))
                .andExpect(jsonPath("$.message").value("종료된 시청방에서는 투표를 만들 수 없습니다."));

        assertThat(pollRepository.count()).isZero();
    }

    @Test
    @DisplayName("공백을 제거한 선택지가 중복되면 400을 반환하고 투표를 저장하지 않는다")
    void failWhenDuplicateOption() throws Exception {
        mockMvc.perform(post(CREATE_POL_URL, room.getId())
                    .header(HttpHeaders.AUTHORIZATION, bearerToken(host))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody("원영", "원영 ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.message").value("잘못된 입력값입니다."));

        assertAll(
                () -> assertThat(pollRepository.count()).isZero(),
                () -> assertThat(pollOptionRepository.count()).isZero()
        );
    }

    private String bearerToken(Member member) {
        return "Bearer " + jwtTokenProvider.createAccessToken(member.getId());
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

    private static String requestBody(String... contents) {
        String pollOptions = Arrays.stream(contents)
                .map(content -> "{\"content\": \"%s\"}".formatted(content))
                .collect(Collectors.joining(", "));
        return """
                { "question": "엔딩포즈 제일 잘 한 멤버는?", "pollOptions": [%s], "durationMinutes": 10 }
                """.formatted(pollOptions);
    }
}
