package com.comebackroom.domain.report.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "popular_scenes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PopularScene {

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

    @Column(name = "score", nullable = false)
    private Integer score;

    @Column(name = "rank", nullable = false)
    private Integer rank;

    @Builder
    private PopularScene(Report report, Integer startSeconds, Integer endSeconds,
                         Integer score, Integer rank){
        this.report = report;
        this.startSeconds = startSeconds;
        this.endSeconds = endSeconds;
        this.score = score;
        this.rank = rank;
    }
}
