package com.comebackroom.domain.poll.repository;

import com.comebackroom.domain.poll.entity.Poll;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PollRepository extends JpaRepository<Poll, Long> {

    @Query("""
    SELECT p
    FROM Poll p
    JOIN FETCH p.pollOptionList o
    WHERE p.room.id = :roomId
    ORDER BY o.id ASC
    """)
    List<Poll> findAllWithOptionsByRoomId(@Param("roomId") Long roomId);

    @Query("""
    SELECT p
    FROM Poll p
    JOIN FETCH p.room
    WHERE p.id = :pollId
    """)
    Optional<Poll> findWithRoomById(@Param("pollId") Long pollId);
}
