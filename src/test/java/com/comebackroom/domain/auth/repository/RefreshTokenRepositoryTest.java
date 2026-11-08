package com.comebackroom.domain.auth.repository;

import com.comebackroom.TestcontainersConfiguration;
import com.comebackroom.domain.auth.entity.RefreshToken;
import com.comebackroom.domain.member.entity.Member;
import com.comebackroom.domain.member.repository.MemberRepository;
import com.comebackroom.global.config.JpaConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@Import({TestcontainersConfiguration.class, JpaConfig.class})
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("RefreshToken Repository")
class RefreshTokenRepositoryTest {

    private static final String TOKEN_HASH = "a".repeat(64);
    private static final String SECOND_TOKEN_HASH = "b".repeat(64);
    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 11, 20, 12, 0);

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Member member;

    @BeforeEach
    void setUp() {
        member = memberRepository.save(Member.createLocalMember(
                "local-member",
                "encoded-password",
                "local@example.com"
        ));
    }

    @Test
    @DisplayName("토큰 해시로 Refresh Token과 회원을 조회한다")
    void findByTokenHash() {
        refreshTokenRepository.saveAndFlush(RefreshToken.issueFor(member, TOKEN_HASH, EXPIRES_AT));
        entityManager.clear();

        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(TOKEN_HASH)
                .orElseThrow();

        assertAll(
                () -> assertThat(refreshToken.getTokenHash()).isEqualTo(TOKEN_HASH),
                () -> assertThat(refreshToken.getExpiresAt()).isEqualTo(EXPIRES_AT),
                () -> assertThat(refreshToken.getCreatedAt()).isNotNull(),
                () -> assertThat(refreshToken.getMember().getId()).isEqualTo(member.getId())
        );
    }

    @Test
    @DisplayName("토큰 회전을 위해 Refresh Token을 쓰기 잠금으로 조회한다")
    void findWithLockByTokenHash() {
        refreshTokenRepository.saveAndFlush(RefreshToken.issueFor(member, TOKEN_HASH, EXPIRES_AT));
        entityManager.clear();

        RefreshToken refreshToken = refreshTokenRepository
                .findWithLockByTokenHash(TOKEN_HASH)
                .orElseThrow();

        assertThat(refreshToken.getMember().getId()).isEqualTo(member.getId());
    }

    @Test
    @DisplayName("한 회원이 여러 Refresh Token을 저장할 수 있다")
    void saveMultipleRefreshTokensForMember() {
        refreshTokenRepository.save(RefreshToken.issueFor(member, TOKEN_HASH, EXPIRES_AT));
        refreshTokenRepository.save(RefreshToken.issueFor(member, SECOND_TOKEN_HASH, EXPIRES_AT));
        refreshTokenRepository.flush();

        assertAll(
                () -> assertThat(refreshTokenRepository.findByTokenHash(TOKEN_HASH)).isPresent(),
                () -> assertThat(refreshTokenRepository.findByTokenHash(SECOND_TOKEN_HASH)).isPresent(),
                () -> assertThat(refreshTokenRepository.count()).isEqualTo(2)
        );
    }

    @Test
    @DisplayName("동일한 토큰 해시는 중복 저장할 수 없다")
    void rejectDuplicateTokenHash() {
        refreshTokenRepository.saveAndFlush(RefreshToken.issueFor(member, TOKEN_HASH, EXPIRES_AT));

        assertThatThrownBy(
                () -> refreshTokenRepository.saveAndFlush(
                        RefreshToken.issueFor(member, TOKEN_HASH, EXPIRES_AT.plusDays(1))
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("SHA-256 해시 길이가 아니면 데이터베이스에 저장할 수 없다")
    void rejectInvalidTokenHashLengthAtDatabase() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                        INSERT INTO refresh_tokens (
                            member_id,
                            token_hash,
                            expires_at,
                            created_at
                        ) VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                        """,
                member.getId(),
                "short-hash",
                EXPIRES_AT
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("회원이 삭제되면 해당 회원의 Refresh Token도 삭제된다")
    void deleteRefreshTokensWhenMemberIsDeleted() {
        refreshTokenRepository.saveAndFlush(RefreshToken.issueFor(member, TOKEN_HASH, EXPIRES_AT));
        entityManager.clear();

        memberRepository.deleteById(member.getId());
        memberRepository.flush();
        entityManager.clear();

        assertThat(refreshTokenRepository.findByTokenHash(TOKEN_HASH)).isEmpty();
    }
}
