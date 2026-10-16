package com.comebackroom.domain.watchingroom.entity;

import com.comebackroom.domain.tag.entity.Tag;
import com.comebackroom.global.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "watching_room_tags")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WatchingRoomTag extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private WatchingRoom room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id", nullable = false)
    private Tag tag;

    @Builder
    private WatchingRoomTag(WatchingRoom room, Tag tag) {
        this.room = room;
        this.tag = tag;
    }
}
