package com.comebackroom.domain.watchingroom.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

public class WatchingRoomTest {

    // 21:00 시작, 영상 길이 1시간 → 종료 예정 22:00
    private static final LocalDateTime START_AT = LocalDateTime.of(2026, 11, 26, 21, 0);

    private final WatchingRoom room = WatchingRoom.builder()
            .title("title")
            .videoUrl("url")
            .videoTitle("video")
            .videoThumbnailUrl("thumbnail")
            .videoDurationSeconds(3600)
            .maxParticipants(10)
            .isPublic(true)
            .startAt(START_AT)
            .build();


    @ParameterizedTest(name = "{0} → 입장 가능 {1}")
    @CsvSource({
            "2026-11-26T20:29:59, false",   // 시작 30분 전보다 1초 이름
            "2026-11-26T20:30:00, true",    // 시작 30분 전 정각 (포함)
            "2026-11-26T21:59:59, true",
            "2026-11-26T22:00:00, false"    // 종료 예정 정각 (미포함)
    })
    @DisplayName("입장은 시작 30분 전부터 종료 예정 시각 전까지 가능하다")
    void isJoinable_boundaries(LocalDateTime now, boolean expected) {
        assertThat(room.isJoinable(now)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} → 방송 중 {1}")
    @CsvSource({
            "2026-11-26T20:59:59, false",
            "2026-11-26T21:00:00, true",    // 시작 정각 (포함)
            "2026-11-26T21:59:59, true",
            "2026-11-26T22:00:00, false"    // 종료 예정 정각 (미포함)
    })
    @DisplayName("방송 중은 시작 시각부터 종료 예정 시각 전까지다")
    void isLiveAt_boundaries(LocalDateTime now, boolean expected) {
        assertThat(room.isLiveAt(now)).isEqualTo(expected);
    }
}