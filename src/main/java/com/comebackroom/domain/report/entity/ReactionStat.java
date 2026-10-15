package com.comebackroom.domain.report.entity;

import com.comebackroom.domain.reaction.enums.ReactionType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "reaction_stats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReactionStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;

    @Enumerated(EnumType.STRING)
    @Column(name = "reaction_type", length = 20, nullable = false)
    private ReactionType type;

    @Column(name = "count", nullable = false)
    private Integer count;

    @Builder
    private ReactionStat(Report report, ReactionType type, Integer count){
        this.report = report;
        this.type = type;
        this.count = count;
    }
}
