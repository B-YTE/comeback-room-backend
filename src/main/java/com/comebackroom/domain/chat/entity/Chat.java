package com.comebackroom.domain.chat.entity;

import com.comebackroom.domain.member.entity.Member;
import com.comebackroom.domain.watchingroom.entity.WatchingRoom;
import com.comebackroom.global.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "chats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Chat extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private WatchingRoom room;

    @Column(name = "video_position", nullable = false)
    private Integer videoPosition;

    @Column(name = "content", length = 500, nullable = false)
    private String content;

    @Builder
    private Chat(Member member, WatchingRoom room,
                 Integer videoPosition, String content) {
        this.member = member;
        this.room = room;
        this.videoPosition = videoPosition;
        this.content = content;
    }
}
