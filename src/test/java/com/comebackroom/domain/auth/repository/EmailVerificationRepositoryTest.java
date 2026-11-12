package com.comebackroom.domain.auth.repository;

import com.comebackroom.TestcontainersConfiguration;
import com.comebackroom.domain.auth.entity.EmailVerification;
import com.comebackroom.domain.auth.enums.EmailVerificationPurpose;
import com.comebackroom.global.config.JpaConfig;
import jakarta.persistence.EntityManager;
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
@DisplayName("EmailVerification Repository")
class EmailVerificationRepositoryTest {

    private static final String EMAIL = "zin@example.com";
    private static final String CODE_HASH = "a".repeat(64);
    private static final String SECOND_CODE_HASH = "b".repeat(64);
    private static final String TOKEN_HASH = "c".repeat(64);
    private static final LocalDateTime ISSUED_AT = LocalDateTime.of(2026, 11, 12, 12, 0);

    @Autowired
    private EmailVerificationRepository emailVerificationRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("이메일과 목적에 연결된 인증 정보를 저장하고 쓰기 잠금으로 조회한다")
    void findWithLockByEmailAndPurpose() {
        EmailVerification saved = emailVerificationRepository.saveAndFlush(
                createEmailVerification(EmailVerificationPurpose.SIGN_UP, CODE_HASH)
        );
        entityManager.clear();

        EmailVerification found = emailVerificationRepository
                .findWithLockByEmailAndPurpose(EMAIL, EmailVerificationPurpose.SIGN_UP)
                .orElseThrow();

        assertAll(
                () -> assertThat(found.getId()).isEqualTo(saved.getId()),
                () -> assertThat(found.getEmail()).isEqualTo(EMAIL),
                () -> assertThat(found.getPurpose())
                        .isEqualTo(EmailVerificationPurpose.SIGN_UP),
                () -> assertThat(found.getCreatedAt()).isNotNull()
        );
    }

    @Test
    @DisplayName("동일한 이메일은 목적별로 각각 인증 정보를 저장할 수 있다")
    void saveSameEmailForDifferentPurposes() {
        emailVerificationRepository.save(
                createEmailVerification(EmailVerificationPurpose.SIGN_UP, CODE_HASH)
        );
        emailVerificationRepository.save(
                createEmailVerification(EmailVerificationPurpose.FIND_ID, SECOND_CODE_HASH)
        );
        emailVerificationRepository.flush();

        assertAll(
                () -> assertThat(emailVerificationRepository.findByEmailAndPurpose(
                        EMAIL,
                        EmailVerificationPurpose.SIGN_UP
                )).isPresent(),
                () -> assertThat(emailVerificationRepository.findByEmailAndPurpose(
                        EMAIL,
                        EmailVerificationPurpose.FIND_ID
                )).isPresent()
        );
    }

    @Test
    @DisplayName("동일한 이메일과 목적의 인증 정보는 중복 저장할 수 없다")
    void rejectDuplicateEmailAndPurpose() {
        emailVerificationRepository.saveAndFlush(
                createEmailVerification(EmailVerificationPurpose.SIGN_UP, CODE_HASH)
        );

        assertThatThrownBy(() -> emailVerificationRepository.saveAndFlush(
                createEmailVerification(EmailVerificationPurpose.SIGN_UP, SECOND_CODE_HASH)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("인증 성공 후 토큰 해시로 이메일 인증 정보를 조회한다")
    void findByVerificationTokenHash() {
        EmailVerification emailVerification = createEmailVerification(
                EmailVerificationPurpose.SIGN_UP,
                CODE_HASH
        );
        emailVerification.completeVerification(
                TOKEN_HASH,
                ISSUED_AT.plusMinutes(31),
                ISSUED_AT.plusMinutes(1)
        );
        emailVerificationRepository.saveAndFlush(emailVerification);
        entityManager.clear();

        EmailVerification found = emailVerificationRepository
                .findByVerificationTokenHash(TOKEN_HASH)
                .orElseThrow();

        assertAll(
                () -> assertThat(found.getEmail()).isEqualTo(EMAIL),
                () -> assertThat(found.getPurpose())
                        .isEqualTo(EmailVerificationPurpose.SIGN_UP),
                () -> assertThat(found.getVerificationTokenHash()).isEqualTo(TOKEN_HASH)
        );
    }

    @Test
    @DisplayName("SHA-256 길이가 아닌 인증코드 해시는 데이터베이스에 저장할 수 없다")
    void rejectInvalidCodeHashLengthAtDatabase() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                        INSERT INTO email_verifications (
                            email,
                            purpose,
                            code_hash,
                            code_expires_at,
                            verification_attempts,
                            request_count,
                            request_window_started_at,
                            last_sent_at,
                            created_at
                        ) VALUES (?, ?, ?, ?, 0, 1, ?, ?, CURRENT_TIMESTAMP)
                        """,
                EMAIL,
                EmailVerificationPurpose.SIGN_UP.name(),
                "short-hash",
                ISSUED_AT.plusMinutes(5),
                ISSUED_AT,
                ISSUED_AT
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private static EmailVerification createEmailVerification(
            EmailVerificationPurpose purpose,
            String codeHash
    ) {
        return EmailVerification.issue(
                EMAIL,
                purpose,
                codeHash,
                ISSUED_AT.plusMinutes(5),
                ISSUED_AT
        );
    }
}
