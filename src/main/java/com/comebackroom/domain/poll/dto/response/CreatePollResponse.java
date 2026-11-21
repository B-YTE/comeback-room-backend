package com.comebackroom.domain.poll.dto.response;

import com.comebackroom.domain.poll.entity.Poll;
import com.comebackroom.domain.poll.entity.PollOption;
import com.comebackroom.domain.poll.enums.PollStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

public record CreatePollResponse(
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
        private static PollOptionResponse from(PollOption pollOption) {
            return new PollOptionResponse(pollOption.getId(), pollOption.getContent(), 0, false);
        }
    }

    public static CreatePollResponse from(Poll poll) {
        return new CreatePollResponse(
                poll.getId(),
                poll.getQuestion(),
                poll.getStatus(),
                0,
                poll.getPollOptionList().stream()
                        .map(PollOptionResponse::from)
                        .toList(),
                poll.getExpiresAt(),
                poll.getCreatedAt()
        );
    }
}
