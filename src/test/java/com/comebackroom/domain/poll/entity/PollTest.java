package com.comebackroom.domain.poll.entity;

import com.comebackroom.domain.poll.enums.PollStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Poll 엔티티")
class PollTest {

    private static final LocalDateTime CREATE_TIME = LocalDateTime.of(2026, 11, 20, 18, 0);
    private static final int DURATION_MINUTES = 10;
    private static final LocalDateTime EXPIRES_AT = CREATE_TIME.plusMinutes(DURATION_MINUTES);

    @Test
    @DisplayName("마감 시각 전이면 ONGOING이다")
    void ongoingBeforeExpiresAt() {
        // given
        Poll poll = createPoll();

        // when
        PollStatus status = poll.currentStatus(EXPIRES_AT.minusSeconds(1));

        // then
        assertThat(status).isEqualTo(PollStatus.ONGOING);
    }

    @Test
    @DisplayName("정확히 마감 시각이면 ENDED이다")
    void endedAtExpiresAt() {
        // given
        Poll poll = createPoll();

        // when
        PollStatus status = poll.currentStatus(EXPIRES_AT);

        // then
        assertThat(status).isEqualTo(PollStatus.ENDED);
    }

    @Test
    @DisplayName("마감 시각이 지나면 ENDED이다")
    void endedAfterExpiresAt() {
        // given
        Poll poll = createPoll();

        // when
        PollStatus status = poll.currentStatus(EXPIRES_AT.plusSeconds(1));

        // then
        assertThat(status).isEqualTo(PollStatus.ENDED);
    }

    private Poll createPoll() {
        return Poll.create(
                null,
                "엔딩포즈 제일 잘 한 멤버는?",
                List.of("원영", "유진"),
                CREATE_TIME,
                DURATION_MINUTES
        );
    }
}
