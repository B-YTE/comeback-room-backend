package com.comebackroom.domain.auth.entity;

import com.comebackroom.domain.member.entity.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayName("RefreshToken 엔티티")
class RefreshTokenTest {

    private static final String TOKEN_HASH = "a".repeat(64);
    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 11, 20, 12, 0);

    private final Member member = Member.createLocalMember(
            "local-member",
            "encoded-password",
            "local@example.com"
    );

    @Test
    @DisplayName("회원과 토큰 해시 및 만료시간으로 Refresh Token을 생성한다")
    void issueRefreshToken() {
        RefreshToken refreshToken = RefreshToken.issueFor(member, TOKEN_HASH, EXPIRES_AT);

        assertAll(
                () -> assertThat(refreshToken.getMember()).isEqualTo(member),
                () -> assertThat(refreshToken.getTokenHash()).isEqualTo(TOKEN_HASH),
                () -> assertThat(refreshToken.getExpiresAt()).isEqualTo(EXPIRES_AT)
        );
    }

    @Test
    @DisplayName("필수값이 없으면 Refresh Token을 생성할 수 없다")
    void failToCreateRefreshTokenWithoutRequiredValue() {
        assertAll(
                () -> assertThatThrownBy(
                        () -> RefreshToken.issueFor(null, TOKEN_HASH, EXPIRES_AT)
                ).isInstanceOf(IllegalArgumentException.class),
                () -> assertThatThrownBy(
                        () -> RefreshToken.issueFor(member, " ", EXPIRES_AT)
                ).isInstanceOf(IllegalArgumentException.class),
                () -> assertThatThrownBy(
                        () -> RefreshToken.issueFor(member, TOKEN_HASH, null)
                ).isInstanceOf(IllegalArgumentException.class)
        );
    }

    @Test
    @DisplayName("SHA-256 해시 길이가 아니면 Refresh Token을 생성할 수 없다")
    void failToCreateRefreshTokenWithInvalidHashLength() {
        assertThatThrownBy(
                () -> RefreshToken.issueFor(member, "short-hash", EXPIRES_AT)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tokenHash는 64자여야 합니다.");
    }
}
