package com.comebackroom.domain.poll.repository;

import com.comebackroom.domain.poll.dto.query.PollOptionVoteCount;
import com.comebackroom.domain.poll.entity.PollVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PollVoteRepository extends JpaRepository<PollVote, Long> {

    @Query("""
    SELECT NEW com.comebackroom.domain.poll.dto.query.PollOptionVoteCount(
            v.pollOption.id,
            COUNT(v),
            SUM(CASE WHEN v.member.id = :memberId THEN 1 ELSE 0 END)
        )
    FROM PollVote v
    WHERE v.poll.id IN :pollIds
    GROUP BY v.pollOption.id
    """)
    List<PollOptionVoteCount> countByPollIds(@Param("pollIds") List<Long> pollIds,
                                             @Param("memberId") Long memberId);
}
