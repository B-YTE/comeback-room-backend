package com.comebackroom.domain.poll.entity;

import com.comebackroom.domain.member.entity.Member;
import com.comebackroom.global.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "poll_votes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PollVote extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poll_id", nullable = false)
    private Poll poll;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poll_option_id", nullable = false)
    private PollOption pollOption;

    @Builder
    private PollVote(Poll poll, Member member, PollOption pollOption) {
        this.poll = poll;
        this.member = member;
        this.pollOption = pollOption;
    }

}
