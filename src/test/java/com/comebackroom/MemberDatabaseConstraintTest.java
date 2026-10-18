package com.comebackroom;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@DataJpaTest
@DisplayName("Member 데이터베이스 제약조건")
class MemberDatabaseConstraintTest {

    private static final String INSERT_MEMBER_SQL = """
            INSERT INTO members (
                login_id,
                password,
                email,
                provider,
                provider_id,
                created_at
            ) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("유효한 LOCAL 회원과 GOOGLE 회원은 저장할 수 있다")
    void saveValidMembers() {
        assertThatCode(() -> {
            insertMember("local-member", "encoded-password", "local@example.com", "LOCAL", null);
            insertMember(null, null, "google@example.com", "GOOGLE", "google-subject-1");
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("LOCAL 회원의 필수값이 없으면 저장할 수 없다")
    void rejectInvalidLocalMember() {
        assertThatThrownBy(
                () -> insertMember("local-member", null, "local@example.com", "LOCAL", null)
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("GOOGLE 회원의 필드 조합이 올바르지 않으면 저장할 수 없다")
    void rejectInvalidGoogleMember() {
        assertThatThrownBy(
                () -> insertMember("google-login-id", null, "google@example.com", "GOOGLE", "google-subject-1")
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("동일한 Google 계정은 중복 저장할 수 없다")
    void rejectDuplicateGoogleAccount() {
        insertMember(null, null, "first@example.com", "GOOGLE", "same-google-subject");

        assertThatThrownBy(
                () -> insertMember(null, null, "second@example.com", "GOOGLE", "same-google-subject")
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertMember(
            String loginId,
            String password,
            String email,
            String provider,
            String providerId
    ) {
        jdbcTemplate.update(
                INSERT_MEMBER_SQL,
                loginId,
                password,
                email,
                provider,
                providerId
        );
    }
}
