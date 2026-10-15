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

    @Column(name = "start_second", nullable = false)
    private Integer startSecond;

    @Column(name = "end_second", nullable = false)
    private Integer endSecond;

    @Column(name = "reaction_count", nullable = false)
    private Integer reactionCount;

    @Builder
    private ReactionTimeline(Report report, Integer startSecond,
                             Integer endSecond, Integer reactionCount){
        this.report = report;
        this.startSecond = startSecond;
        this.endSecond = endSecond;
        this.reactionCount = reactionCount;
    }
}
