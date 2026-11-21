package com.comebackroom.domain.poll.service;

import com.comebackroom.domain.poll.dto.request.CreatePollRequest;
import com.comebackroom.domain.poll.dto.response.CreatePollResponse;
import com.comebackroom.domain.poll.entity.Poll;
import com.comebackroom.domain.poll.exception.PollErrorCode;
import com.comebackroom.domain.poll.repository.PollRepository;
import com.comebackroom.domain.watchingroom.entity.WatchingRoom;
import com.comebackroom.domain.watchingroom.enums.RoomStatus;
import com.comebackroom.domain.watchingroom.exception.WatchingRoomErrorCode;
import com.comebackroom.domain.watchingroom.repository.WatchingRoomRepository;
import com.comebackroom.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
public class PollService {

    private final PollRepository pollRepository;
    private final WatchingRoomRepository watchingRoomRepository;
    private final Clock clock;

    @Transactional
    public CreatePollResponse createPoll(Long roomId, Long memberId, CreatePollRequest request) {
        WatchingRoom room = getWatchingRoom(roomId);
        validateHost(room, memberId);
        validateRoomNotEnded(room);

        Poll poll = Poll.create(
                room,
                request.question(),
                request.optionContents(),
                LocalDateTime.now(clock),
                request.durationMinutes()
        );
        pollRepository.save(poll);

        log.info("투표 생성 완료 pollId={}, roomId={}, memberId={}", poll.getId(), roomId, memberId);
        return CreatePollResponse.from(poll);
    }

    private WatchingRoom getWatchingRoom(Long roomId) {
        return watchingRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(WatchingRoomErrorCode.WATCHING_ROOM_NOT_FOUND));
    }

    private void validateHost(WatchingRoom room, Long memberId) {
        if (!room.getHost().getId().equals(memberId)) {
            throw new BusinessException(PollErrorCode.POLL_CREATE_FORBIDDEN);
        }
    }

    private void validateRoomNotEnded(WatchingRoom room) {
        if (room.getStatus() == RoomStatus.ENDED) {
            throw new BusinessException(PollErrorCode.WATCHING_ROOM_ALREADY_ENDED);
        }
    }
}
