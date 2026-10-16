package com.comebackroom.domain.watchingroom.repository;

import com.comebackroom.domain.watchingroom.entity.RoomParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomParticipantRepository extends JpaRepository<RoomParticipant, Long> {
}
