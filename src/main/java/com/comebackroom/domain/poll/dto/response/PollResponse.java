package com.comebackroom.domain.poll.dto.response;

import com.comebackroom.domain.poll.dto.query.PollOptionVoteCount;
import com.comebackroom.domain.poll.entity.Poll;
import com.comebackroom.domain.poll.entity.PollOption;
import com.comebackroom.domain.poll.enums.PollStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record PollResponse(
        Long pollId,
        String question,
        PollStatus status,
        int totalVoteCount,
        List<PollOptionResponse> pollOptions,
        LocalDateTime expiresAt,
        LocalDateTime createdAt
) {

    public record PollOptionResponse(
            Long pollOptionId,
            String content,
            int voteCount,
            @JsonProperty("isSelected")
            boolean isSelected
    ) {
        public static PollOptionResponse of(PollOption option, PollOptionVoteCount count) {
            if (count == null) {
                return new PollOptionResponse(option.getId(), option.getContent(), 0, false);
            }
            return new PollOptionResponse(option.getId(), option.getContent(),
                    count.voteCount().intValue(), count.isSelected());
        }
    }

    // 생성 직후
    public static PollResponse ofCreated(Poll poll, LocalDateTime now) {
        List<PollOptionResponse> options = poll.getPollOptionList().stream()
                .map(option -> PollOptionResponse.of(option, null))
                .toList();
        return from(poll, options, now);
    }

    // 집계 결과 반영
    public static PollResponse of(Poll poll, Map<Long, PollOptionVoteCount> voteCounts, LocalDateTime now) {
        List<PollOptionResponse> options = poll.getPollOptionList().stream()
                .map(option -> PollOptionResponse.of(option, voteCounts.get(option.getId())))
                .toList();
        return from(poll, options, now);
    }

    private static PollResponse from(Poll poll, List<PollOptionResponse> options, LocalDateTime now) {
        int totalVoteCount = options.stream()
                .mapToInt(PollOptionResponse::voteCount)
                .sum();
        return new PollResponse(poll.getId(), poll.getQuestion(), poll.currentStatus(now),
                totalVoteCount, options, poll.getExpiresAt(), poll.getCreatedAt());
    }
}
