package com.comebackroom.domain.poll.repository;

import com.comebackroom.domain.poll.entity.PollVote;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PollVoteRepository extends JpaRepository<PollVote, Long> {
}
