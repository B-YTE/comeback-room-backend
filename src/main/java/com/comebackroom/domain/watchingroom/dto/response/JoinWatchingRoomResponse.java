package com.comebackroom.domain.watchingroom.dto.response;

import com.comebackroom.domain.watchingroom.entity.WatchingRoom;
import com.comebackroom.domain.watchingroom.enums.RoomStatus;

import java.time.Duration;
import java.time.LocalDateTime;

public record JoinWatchingRoomResponse(
        Long roomId,
        RoomStatus status,
        LocalDateTime serverTime,
        String ticket,
        String tabId,
        Integer ticketExpiresInSeconds
) {
    public static JoinWatchingRoomResponse of(
            Long roomId, RoomStatus status, LocalDateTime now,
            String ticket, String tabId, Duration ticketTTL
    ) {
        return new JoinWatchingRoomResponse(
                roomId, status, now,
                ticket, tabId, (int) ticketTTL.toSeconds()
        );
    }
}
