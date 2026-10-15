package com.comebackroom.domain.poll.entity;

import com.comebackroom.domain.poll.enums.PollStatus;
import com.comebackroom.domain.watchingroom.entity.WatchingRoom;
import com.comebackroom.global.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "polls")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Poll extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private WatchingRoom room;

    @Column(name = "question", length = 255, nullable = false)
    private String question;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50, nullable = false)
    private PollStatus status;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Builder
    private Poll(WatchingRoom room, String question,
                 PollStatus status, LocalDateTime expiresAt) {
        this.room = room;
        this.question = question;
        this.status = status;
        this.expiresAt = expiresAt;
    }
}
