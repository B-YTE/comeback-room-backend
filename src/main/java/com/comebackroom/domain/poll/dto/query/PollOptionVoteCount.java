package com.comebackroom.domain.poll.dto.query;

public record PollOptionVoteCount(
        Long pollOptionId,
        Long voteCount,
        Long myVoteCount
) {
    public boolean isSelected() {
        return myVoteCount > 0;
    }
}
