package com.comebackroom.domain.notification.entity;

import com.comebackroom.domain.member.entity.Member;
import com.comebackroom.domain.notification.enums.NotificationType;
import com.comebackroom.domain.watchingroom.entity.WatchingRoom;
import com.comebackroom.global.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private WatchingRoom room;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 30, nullable = false)
    private NotificationType type;

    @Column(name = "title", length = 100, nullable = false)
    private String title;

    @Column(name = "content", length = 255)
    private String content;

    @Column(name = "is_read", nullable = false)
    private Boolean isRead;

    @Builder
    private Notification(Member member, WatchingRoom room, NotificationType type,
                         String title, String content) {
        this.member = member;
        this.room = room;
        this.type = type;
        this.title = title;
        this.content = content;

        this.isRead = false;
    }
}
