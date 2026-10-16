package com.comebackroom.domain.watchingroom.entity;

import com.comebackroom.domain.member.entity.Member;
import com.comebackroom.global.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "room_participants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomParticipant extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private WatchingRoom room;

    @Builder
    private RoomParticipant(Member member, WatchingRoom room) {
        this.member = member;
        this.room = room;
    }
}
