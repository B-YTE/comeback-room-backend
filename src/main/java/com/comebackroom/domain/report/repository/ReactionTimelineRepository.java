package com.comebackroom.domain.report.repository;

import com.comebackroom.domain.report.entity.ReactionTimeline;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReactionTimelineRepository extends JpaRepository<ReactionTimeline, Long> {
}
