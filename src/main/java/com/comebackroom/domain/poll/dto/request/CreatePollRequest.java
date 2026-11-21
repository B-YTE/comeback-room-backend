package com.comebackroom.domain.poll.dto.request;

import com.comebackroom.domain.poll.entity.Poll;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

public record CreatePollRequest(
        @NotBlank
        @Size(max = 50)
        String question,

        @NotNull
        @Size(min = Poll.MIN_OPTION_COUNT, max = Poll.MAX_OPTION_COUNT)
        List<@NotNull @Valid PollOptionRequest> pollOptions,

        @NotNull
        @Min(Poll.MIN_DURATION_MINUTES)
        @Max(Poll.MAX_DURATION_MINUTES)
        Integer durationMinutes
) {

    public record PollOptionRequest(
            @NotBlank
            @Size(max = 20)
            String content
    ) {
    }

    public List<String> optionContents() {
        return pollOptions.stream()
                .map(PollOptionRequest::content)
                .toList();
    }
}
