package com.comebackroom.domain.poll.repository;

import com.comebackroom.domain.poll.entity.PollOption;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PollOptionRepository extends JpaRepository<PollOption, Long> {
}
