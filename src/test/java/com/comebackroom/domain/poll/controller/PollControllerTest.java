package com.comebackroom.domain.poll.controller;

import com.comebackroom.domain.poll.dto.request.CreatePollRequest;
import com.comebackroom.domain.poll.dto.response.CreatePollResponse;
import com.comebackroom.domain.poll.dto.response.CreatePollResponse.PollOptionResponse;
import com.comebackroom.domain.poll.enums.PollStatus;
import com.comebackroom.domain.poll.exception.PollErrorCode;
import com.comebackroom.domain.poll.service.PollService;
import com.comebackroom.domain.watchingroom.exception.WatchingRoomErrorCode;
import com.comebackroom.global.exception.BusinessException;
import com.comebackroom.global.exception.GlobalExceptionHandler;
import com.comebackroom.global.security.support.WithMockMember;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(controllers = PollController.class)
@DisplayName("투표 API")
class PollControllerTest {

    private static final long ROOM_ID = 3L;
    private static final long HOST_ID = 7L;
    private static final long OTHER_MEMBER_ID = 8L;
    private static final String CREATE_POLL_URL = "/api/v1/watching-rooms/{roomId}/polls";
    private static final String CREATE_POLL_PATH = "/api/v1/watching-rooms/3/polls";
    private static final String VALID_REQUEST_BODY = """
            {
                "question": "엔딩포즈 제일 잘 한 멤버는?",
                "pollOptions": [
                    { "content": "원영" },
                    { "content": "유진" }
                ],
                "durationMinutes": 10
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PollService pollService;

    @Test
    @WithMockMember(memberId = HOST_ID)
    @DisplayName("투표를 생성하면 201과 생성된 투표 정보를 반환한다")
    void createPoll() throws Exception {
        // given
        CreatePollResponse response = new CreatePollResponse(
                1L,
                "엔딩포즈 제일 잘 한 멤버는?",
                PollStatus.ONGOING,
                0,
                List.of(
                        new PollOptionResponse(10L, "원영", 0, false),
                        new PollOptionResponse(11L, "유진", 0, false)
                ),
                LocalDateTime.of(2026, 11, 19, 21, 10),
                LocalDateTime.of(2026, 11, 19, 21, 0)
        );
        given(pollService.createPoll(eq(ROOM_ID), eq(HOST_ID), any(CreatePollRequest.class)))
                .willReturn(response);

        // when & then
        mockMvc.perform(post(CREATE_POLL_URL, ROOM_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_REQUEST_BODY))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.code").value("POLL_CREATED"))
                .andExpect(jsonPath("$.message").value("투표가 성공적으로 생성되었습니다."))
                .andExpect(jsonPath("$.data.pollId").value(1))
                .andExpect(jsonPath("$.data.status").value("ONGOING"))
                .andExpect(jsonPath("$.data.totalVoteCount").value(0))
                .andExpect(jsonPath("$.data.pollOptions.length()").value(2))
                .andExpect(jsonPath("$.data.pollOptions[0].pollOptionId").value(10))
                .andExpect(jsonPath("$.data.pollOptions[0].content").value("원영"))
                .andExpect(jsonPath("$.data.pollOptions[0].isSelected").value(false))
                .andExpect(jsonPath("$.data.expiresAt").value("2026-11-19T21:10:00"));
    }

    @Test
    @WithMockMember(memberId = OTHER_MEMBER_ID)
    @DisplayName("방장이 아니면 403과 POLL_CREATE_FORBIDDEN을 반환한다")
    void failWhenNotHost() throws Exception {
        // given: Service가 예외를 던지도록 각본 등록
        given(pollService.createPoll(eq(ROOM_ID), eq(OTHER_MEMBER_ID), any(CreatePollRequest.class)))
                .willThrow(new BusinessException(PollErrorCode.POLL_CREATE_FORBIDDEN));

        // when & then
        mockMvc.perform(post(CREATE_POLL_URL, ROOM_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_REQUEST_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("POLL_CREATE_FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("방장만 투표를 생성할 수 있습니다."))
                .andExpect(jsonPath("$.path").value(CREATE_POLL_PATH))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @WithMockMember(memberId = HOST_ID)
    @DisplayName("존재하는 않는 시청방이면 WATCHING_ROOM_NOT_FOUND를 반환한다")
    void failWhenNotRoom() throws Exception {
        // given
        given(pollService.createPoll(eq(ROOM_ID), eq(HOST_ID), any(CreatePollRequest.class)))
                .willThrow(new BusinessException(WatchingRoomErrorCode.WATCHING_ROOM_NOT_FOUND));

        // when & then
        mockMvc.perform(post(CREATE_POLL_URL, ROOM_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_REQUEST_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("WATCHING_ROOM_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("존재하지 않는 시청방입니다."))
                .andExpect(jsonPath("$.path").value(CREATE_POLL_PATH))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @WithMockMember(memberId = HOST_ID)
    @DisplayName("종료된 시청방이면 WATCHING_ROOM_ALREADY_ENDED를 반환한다")
    void failWhenEndedRoom() throws Exception {
        // given
        given(pollService.createPoll(eq(ROOM_ID), eq(HOST_ID), any(CreatePollRequest.class)))
                .willThrow(new BusinessException(PollErrorCode.WATCHING_ROOM_ALREADY_ENDED));

        // when & then
        mockMvc.perform(post(CREATE_POLL_URL, ROOM_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_REQUEST_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("WATCHING_ROOM_ALREADY_ENDED"))
                .andExpect(jsonPath("$.message").value("종료된 시청방에서는 투표를 만들 수 없습니다."))
                .andExpect(jsonPath("$.path").value(CREATE_POLL_PATH))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("invalidRequestBodies")
    @WithMockMember(memberId = HOST_ID)
    @DisplayName("요청 본문이 명세를 어기면 400을 반환하고 Service를 호출하지 않는다")
    void failWithInvalidRequestBody(String description, String requestBody) throws Exception {
        mockMvc.perform(post(CREATE_POLL_URL, ROOM_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.message").value("잘못된 입력값입니다."))
                .andExpect(jsonPath("$.path").value(CREATE_POLL_PATH));

        verify(pollService, never()).createPoll(any(), any(), any());
    }

    private static Stream<Arguments> invalidRequestBodies() {
        return Stream.of(
                Arguments.of("질문이 공백", """
                        {"question": " ", "pollOptions": [{"content": "원영"}, {"content": "유진"}], "durationMinutes": 10}
                        """),
                Arguments.of("질문이 51자", """
                        {"question": "%s", "pollOptions": [{"content": "원영"}, {"content": "유진"}], "durationMinutes": 10}
                        """.formatted("가".repeat(51))),
                Arguments.of("선택지 목록 누락", """
                        {"question": "질문", "durationMinutes": 10}
                        """),
                Arguments.of("선택지가 1개", """
                        {"question": "질문", "pollOptions": [{"content": "원영"}], "durationMinutes": 10}
                        """),
                Arguments.of("선택지가 5개", """
                        {"question": "질문", "pollOptions": [{"content": "원영"}, {"content": "유진"}, {"content": "레이"}, {"content": "리즈"}, {"content": "이서"}], "durationMinutes": 10}
                        """),
                Arguments.of("선택지 내용이 21자", """
                        {"question": "질문", "pollOptions": [{"content": "원영"}, {"content": "%s"}], "durationMinutes": 10}
                        """.formatted("가".repeat(21))),
                Arguments.of("선택지에 null 포함", """
                        {"question": "질문", "pollOptions": [null, {"content": "유진"}], "durationMinutes": 10}
                        """),
                Arguments.of("마감 시간 0분", """
                        {"question": "질문", "pollOptions": [{"content": "원영"}, {"content": "유진"}], "durationMinutes": 0}
                        """),
                Arguments.of("마감 시간 61분", """
                        {"question": "질문", "pollOptions": [{"content": "원영"}, {"content": "유진"}], "durationMinutes": 61}
                        """),
                Arguments.of("마감 시간 누락", """
                        {"question": "질문", "pollOptions": [{"content": "원영"}, {"content": "유진"}]}
                        """)
        );
    }
}
