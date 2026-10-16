package com.comebackroom.domain.watchingroom.entity;

import com.comebackroom.domain.member.entity.Member;
import com.comebackroom.domain.watchingroom.enums.RoomStatus;
import com.comebackroom.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "watching_rooms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WatchingRoom extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_member_id", nullable = false)
    private Member host;

    @Column(name = "title", length = 100, nullable = false)
    private String title;

    @Column(name = "video_url", length = 500, nullable = false)
    private String videoUrl;

    @Column(name = "video_title", length = 200, nullable = false)
    private String videoTitle;

    @Column(name = "video_thumbnail_url", length = 500, nullable = false)
    private String videoThumbnailUrl;

    @Column(name = "video_duration_seconds", nullable = false)
    private Integer videoDurationSeconds;

    @Column(name = "max_participants", nullable = false)
    private Integer maxParticipants;

    @Column(name = "is_public", nullable = false)
    private Boolean isPublic;

    @Column(name = "private_code", length = 50, unique = true)
    private String privateCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50, nullable = false)
    private RoomStatus status;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Builder
    private WatchingRoom(Member host, String title, String videoUrl,
                         String videoTitle, String videoThumbnailUrl, Integer videoDurationSeconds,
                         Integer maxParticipants, Boolean isPublic, String privateCode,
                         LocalDateTime startAt) {

        // 공개방 O + 코드 O / 공개방 X + 코드 X 인 경우 예외
        if (isPublic == (privateCode != null)) {
            throw new IllegalArgumentException("공개방은 코드가 없고, 비공개방은 코드가 있어야 합니다.");
        }

        this.host = host;
        this.title = title;
        this.videoUrl = videoUrl;
        this.videoTitle = videoTitle;
        this.videoThumbnailUrl = videoThumbnailUrl;
        this.videoDurationSeconds = videoDurationSeconds;
        this.maxParticipants = maxParticipants;
        this.isPublic = isPublic;
        this.privateCode = privateCode;
        this.startAt = startAt;

        this.status = RoomStatus.SCHEDULED; // 생성 시의 상태는 SCHEDULED

        this.endedAt = startAt.plusSeconds(videoDurationSeconds); // 종료 예정 시각 (start_at + 영상 길이)

    }

    public void start() {
        if (status != RoomStatus.SCHEDULED) {
            throw new IllegalStateException("SCHEDULED 상태의 방만 시작할 수 있습니다. 현재: " + status);
        }
        this.status = RoomStatus.LIVE;
    }

    public void end() {
        if (status != RoomStatus.LIVE) {
            throw new IllegalStateException("LIVE 상태의 방만 종료할 수 있습니다. 현재: " + status);
        }
        this.status = RoomStatus.ENDED;
    }

    public boolean isDeletable() {
        return status == RoomStatus.SCHEDULED;
    }

}
