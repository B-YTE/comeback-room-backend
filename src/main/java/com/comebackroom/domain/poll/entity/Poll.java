package com.comebackroom.domain.poll.entity;

import com.comebackroom.domain.poll.enums.PollStatus;
import com.comebackroom.domain.watchingroom.entity.WatchingRoom;
import com.comebackroom.global.exception.BusinessException;
import com.comebackroom.global.exception.GlobalErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "polls")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Poll {

    public static final int MIN_OPTION_COUNT = 2;
    public static final int MAX_OPTION_COUNT = 4;
    public static final int MIN_DURATION_MINUTES = 1;
    public static final int MAX_DURATION_MINUTES = 60;

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

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @OneToMany(mappedBy = "poll", cascade = CascadeType.PERSIST)
    @OrderBy("id ASC")
    private List<PollOption> pollOptionList = new ArrayList<>();

    private Poll(WatchingRoom room, String question, LocalDateTime now, int durationMinutes) {
        this.room = room;
        this.question = question;
        this.status = PollStatus.ONGOING;
        this.createdAt = now;
        this.expiresAt = now.plusMinutes(durationMinutes);
    }

    public static Poll create(WatchingRoom room, String question, List<String> optionContents,
                              LocalDateTime now, int durationMinutes) {
        validateDurationMinutes(durationMinutes);
        validateOptionContents(optionContents);

        Poll poll = new Poll(room, question, now, durationMinutes);
        for (String content : optionContents) {
            poll.pollOptionList.add(PollOption.builder()
                    .poll(poll)
                    .content(content.strip())
                    .build());
        }
        return poll;
    }

    public List<PollOption> getPollOptionList() {
        return Collections.unmodifiableList(pollOptionList);
    }

    public PollStatus currentStatus(LocalDateTime now) {
        if (now.isBefore(expiresAt)) {
            return PollStatus.ONGOING;
        }
        return PollStatus.ENDED;
    }

    private static void validateDurationMinutes(int durationMinutes) {
        if (durationMinutes < MIN_DURATION_MINUTES || durationMinutes > MAX_DURATION_MINUTES) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private static void validateOptionContents(List<String> optionContents) {
        if (optionContents == null
                || optionContents.size() < MIN_OPTION_COUNT
                || optionContents.size() > MAX_OPTION_COUNT) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        Set<String> uniqueContents = new HashSet<>();
        for (String content : optionContents) {
            if (content == null || content.isBlank() || !uniqueContents.add(content.strip())) {
                throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
            }
        }
    }
}
