package com.comebackroom.domain.auth.entity;

import com.comebackroom.domain.member.entity.Member;
import com.comebackroom.global.entity.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "refresh_tokens",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_refresh_tokens_token_hash",
                        columnNames = "token_hash"
                )
        },
        indexes = {
                @Index(
                        name = "idx_refresh_tokens_member_id",
                        columnList = "member_id"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseCreatedEntity {

    private static final int TOKEN_HASH_LENGTH = 64;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "member_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_refresh_tokens_member")
    )
    private Member member;

    @Column(name = "token_hash", length = TOKEN_HASH_LENGTH, nullable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    private RefreshToken(Member member, String tokenHash, LocalDateTime expiresAt) {
        validateMember(member);
        validateTokenHash(tokenHash);
        validateExpiresAt(expiresAt);

        this.member = member;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public static RefreshToken issueFor(
            Member member,
            String tokenHash,
            LocalDateTime expiresAt
    ) {
        return new RefreshToken(member, tokenHash, expiresAt);
    }

    private static void validateMember(Member member) {
        if (member == null) {
            throw new IllegalArgumentException("member 값은 필수입니다.");
        }
    }

    private static void validateTokenHash(String tokenHash) {
        if (tokenHash == null || tokenHash.isBlank()) {
            throw new IllegalArgumentException("tokenHash 값은 필수입니다.");
        }
        if (tokenHash.length() != TOKEN_HASH_LENGTH) {
            throw new IllegalArgumentException("tokenHash는 64자여야 합니다.");
        }
    }

    private static void validateExpiresAt(LocalDateTime expiresAt) {
        if (expiresAt == null) {
            throw new IllegalArgumentException("expiresAt 값은 필수입니다.");
        }
    }
}
