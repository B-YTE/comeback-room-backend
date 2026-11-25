package com.comebackroom.domain.watchingroom.service;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WatchingRoomService {

    private final Clock clock;

    private final RoomPresenceStore presenceStore;
    private final WebSocketTicketStore ticketStore;

    private final WatchingRoomRepository watchingRoomRepository;
    private final RoomParticipantRepository roomParticipantRepository;
    private final MemberRepository memberRepository;


    // Redis 대기 동안 DB 커넥션을 붙잡지 않기 위해 @Transactional 생략
    public JoinWatchingRoomResponse joinRoom(
            Long roomId, Long requesterId, JoinWatchingRoomRequest request
    ) {
        LocalDateTime now = LocalDateTime.now(clock);
        WatchingRoom room = getRoom(roomId);

        if (!room.isJoinable(now)) {
            throw new BusinessException(WatchingRoomErrorCode.WATCHING_ROOM_NOT_JOINABLE);
        }

        if (requiresPrivateCode(room, requesterId)) {
            validatePrivateCode(room, request.privateCode());
        }

        boolean isReserved = presenceStore.reserve(
                roomId, requesterId, room.getMaxParticipants(), WebSocketTicketStore.TTL
        );
        if (!isReserved) {
            throw new BusinessException(WatchingRoomErrorCode.WATCHING_ROOM_FULL);
        }

        String tabId = UUID.randomUUID().toString();
        String ticket = ticketStore.issue(requesterId, roomId, tabId);

        boolean live = room.isLiveAt(now);
        if (live) {
            recordParticipation(room, requesterId);
        }

        log.info("시청방 참가 roomId={} memberId={} tabId={} live={}", roomId, requesterId, tabId, live);
        return JoinWatchingRoomResponse.of(
                roomId, live ? RoomStatus.LIVE : RoomStatus.SCHEDULED,
                now, ticket, tabId, WebSocketTicketStore.TTL
        );
    }


    // === Helper method ===
    private WatchingRoom getRoom(Long roomId) {
        return watchingRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(WatchingRoomErrorCode.WATCHING_ROOM_NOT_FOUND));
    }

    private boolean requiresPrivateCode(WatchingRoom room, Long memberId) {
        return !room.getIsPublic()
                && !room.isHost(memberId)
                && !presenceStore.isConnected(room.getId(), memberId)
                && !roomParticipantRepository.existsByRoomIdAndMemberId(room.getId(), memberId);
    }

    private void validatePrivateCode(WatchingRoom room, String privateCode) {
        if (privateCode == null) {
            throw new BusinessException(WatchingRoomErrorCode.PRIVATE_CODE_REQUIRED);
        }
        if (!room.getPrivateCode().equals(privateCode)) {
            throw new BusinessException(WatchingRoomErrorCode.INVALID_PRIVATE_CODE);
        }
    }

    private void recordParticipation(WatchingRoom room, Long memberId) {
        if (roomParticipantRepository.existsByRoomIdAndMemberId(room.getId(), memberId)) {
            return;
        }
        try {
            roomParticipantRepository.save(RoomParticipant.builder()
                    .room(room)
                    .member(memberRepository.getReferenceById(memberId))
                    .build());
        } catch (DataIntegrityViolationException e) {
            if (!roomParticipantRepository.existsByRoomIdAndMemberId(room.getId(), memberId)) {
                throw e;
            }
            log.debug("시청 기록이 동시에 저장되어 넘김 roomId={} memberId={}", room.getId(), memberId);
        }
    }

}
