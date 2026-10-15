package com.comebackroom.domain.poll.repository;

import com.comebackroom.domain.poll.entity.Poll;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PollRepository extends JpaRepository<Poll, Long> {
}
