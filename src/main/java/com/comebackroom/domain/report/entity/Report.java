package com.comebackroom.domain.report.entity;

import com.comebackroom.domain.reaction.enums.ReactionType;
import com.comebackroom.domain.watchingroom.entity.WatchingRoom;
import com.comebackroom.global.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", unique = true, nullable = false)
    private WatchingRoom room;

    @Column(name = "total_participants", nullable = false)
    private Integer totalParticipants;

    @Column(name = "chat_count", nullable = false)
    private Integer chatCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "top_reaction_type", length = 20)
    private ReactionType topReactionType;

    @Builder
    private Report(WatchingRoom room, Integer totalParticipants,
                     Integer chatCount, ReactionType topReactionType){
        this.room = room;
        this.totalParticipants = totalParticipants;
        this.chatCount = chatCount;
        this.topReactionType = topReactionType;
    }
}
