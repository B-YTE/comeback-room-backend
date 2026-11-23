package com.comebackroom.domain.poll.service;

import com.comebackroom.domain.poll.dto.query.PollOptionVoteCount;
import com.comebackroom.domain.poll.dto.request.CreatePollRequest;
import com.comebackroom.domain.poll.dto.response.PollListResponse;
import com.comebackroom.domain.poll.dto.response.PollResponse;
import com.comebackroom.domain.poll.entity.Poll;
import com.comebackroom.domain.poll.exception.PollErrorCode;
import com.comebackroom.domain.poll.repository.PollRepository;
import com.comebackroom.domain.poll.repository.PollVoteRepository;
import com.comebackroom.domain.watchingroom.entity.WatchingRoom;
import com.comebackroom.domain.watchingroom.enums.RoomStatus;
import com.comebackroom.domain.watchingroom.exception.WatchingRoomErrorCode;
import com.comebackroom.domain.watchingroom.repository.RoomParticipantRepository;
import com.comebackroom.domain.watchingroom.repository.WatchingRoomRepository;
import com.comebackroom.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PollService {

    private final PollRepository pollRepository;
    private final PollVoteRepository pollVoteRepository;
    private final WatchingRoomRepository watchingRoomRepository;
    private final RoomParticipantRepository roomParticipantRepository;
    private final Clock clock;

    @Transactional
    public PollResponse createPoll(Long roomId, Long memberId, CreatePollRequest request) {
        WatchingRoom room = getWatchingRoom(roomId);
        validateHost(room, memberId);
        validateRoomNotEnded(room);

        LocalDateTime now = LocalDateTime.now(clock);
        Poll poll = Poll.create(
                room,
                request.question(),
                request.optionContents(),
                now,
                request.durationMinutes()
        );
        pollRepository.save(poll);

        log.info("투표 생성 완료 pollId={}, roomId={}, memberId={}", poll.getId(), roomId, memberId);
        return PollResponse.ofCreated(poll, now);
    }

    @Transactional(readOnly = true)
    public PollListResponse getPolls(Long roomId, Long memberId) {
        WatchingRoom room = getWatchingRoom(roomId);
        validateReadable(room, memberId);

        List<Poll> polls = pollRepository.findAllWithOptionsByRoomId(roomId);
        if (polls.isEmpty()) {
            return PollListResponse.empty();
        }

        List<Long> pollIds = polls.stream()
                .map(Poll::getId)
                .toList();
        Map<Long, PollOptionVoteCount> voteCounts = pollVoteRepository
                .countByPollIds(pollIds, memberId).stream()
                .collect(Collectors.toMap(PollOptionVoteCount::pollOptionId, Function.identity()));

        return PollListResponse.of(polls, voteCounts, LocalDateTime.now(clock));
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

    private void validateReadable(WatchingRoom room, Long memberId) {
        if (room.getIsPublic()) {
            return;
        }
        if (room.getHost().getId().equals(memberId)) {
            return;
        }
        if (roomParticipantRepository.existsByRoomIdAndMemberId(room.getId(), memberId)) {
            return;
        }
        throw new BusinessException(PollErrorCode.NOT_WATCHING_ROOM_PARTICIPANT);
    }
}
