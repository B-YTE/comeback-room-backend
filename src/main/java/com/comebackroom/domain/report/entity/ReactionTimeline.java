package com.comebackroom.domain.report.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "reaction_timelines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReactionTimeline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;

    @Column(name = "start_seconds", nullable = false)
    private Integer startSeconds;

    @Column(name = "end_seconds", nullable = false)
    private Integer endSeconds;

    @Column(name = "reaction_count", nullable = false)
    private Integer reactionCount;

    @Builder
    private ReactionTimeline(Report report, Integer startSeconds,
                             Integer endSeconds, Integer reactionCount){
        this.report = report;
        this.startSeconds = startSeconds;
        this.endSeconds = endSeconds;
        this.reactionCount = reactionCount;
    }
}
