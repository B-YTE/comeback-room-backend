package com.comebackroom.domain.reaction.repository;

import com.comebackroom.domain.reaction.entity.Reaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReactionRepository extends JpaRepository<Reaction, Long> {
}
