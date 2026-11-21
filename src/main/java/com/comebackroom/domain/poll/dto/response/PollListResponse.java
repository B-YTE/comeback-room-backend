package com.comebackroom.domain.poll.dto.response;

import com.comebackroom.domain.poll.dto.query.PollOptionVoteCount;
import com.comebackroom.domain.poll.entity.Poll;
import com.comebackroom.domain.poll.enums.PollStatus;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public record PollListResponse(
        List<PollResponse> polls
) {
   public static PollListResponse of(List<Poll> polls, Map<Long, PollOptionVoteCount> voteCounts, LocalDateTime now) {
       List<PollResponse> responses = polls.stream()
               .map(poll -> PollResponse.of(poll, voteCounts, now))
               .toList();
       return new PollListResponse(sortForDisplay(responses));
   }

   public static PollListResponse empty() {
       return new PollListResponse(List.of());
   }

   private static List<PollResponse> sortForDisplay(List<PollResponse> responses) {
       Comparator<PollResponse> byExpiresAt = Comparator
               .comparing(PollResponse::expiresAt)
               .thenComparing(PollResponse::pollId);

       List<PollResponse> ongoing = responses.stream()
               .filter(poll -> poll.status() == PollStatus.ONGOING)
               .sorted(byExpiresAt)
               .toList();
       List<PollResponse> ended = responses.stream()
               .filter(poll -> poll.status() == PollStatus.ENDED)
               .sorted(byExpiresAt.reversed())
               .toList();

       return Stream.concat(ongoing.stream(), ended.stream()).toList();
   }
}
