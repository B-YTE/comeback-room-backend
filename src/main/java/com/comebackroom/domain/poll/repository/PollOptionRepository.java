package com.comebackroom.domain.poll.repository;

import com.comebackroom.domain.poll.entity.PollOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PollOptionRepository extends JpaRepository<PollOption, Long> {
    @Modifying
    @Query("DELETE FROM PollOption o WHERE o.poll.id = :pollId")
    int bulkDeleteByPollId(@Param("pollId") Long pollId);
}
