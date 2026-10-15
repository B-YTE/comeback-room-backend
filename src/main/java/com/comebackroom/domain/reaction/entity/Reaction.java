package com.comebackroom.domain.reaction.entity;

import com.comebackroom.domain.member.entity.Member;
import com.comebackroom.domain.reaction.enums.ReactionType;
import com.comebackroom.domain.watchingroom.entity.WatchingRoom;
import com.comebackroom.global.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "reactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reaction extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private WatchingRoom room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "reaction_type", length = 20, nullable = false)
    private ReactionType type;

    @Column(name = "video_position_seconds", nullable = false)
    private Integer videoPositionSeconds;

    @Builder
    private Reaction(WatchingRoom room, Member member,
                     ReactionType type, Integer videoPositionSeconds) {
        this.room = room;
        this.member = member;
        this.type = type;
        this.videoPositionSeconds = videoPositionSeconds;
    }

}
