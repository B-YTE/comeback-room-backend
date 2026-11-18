package com.comebackroom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("room-activity 중복 처리 데이터베이스 제약조건")
public class RoomActivityMessageIdConstraintTest {

    private static final String INSERT_MEMBER_SQL = """
            INSERT INTO members (login_id, password, email, provider, created_at)
            VALUES ('activity-member', 'encoded-password', 'activity@example.com', 'LOCAL', CURRENT_TIMESTAMP)
            RETURNING id
            """;

    private static final String INSERT_ROOM_SQL = """
            INSERT INTO watching_rooms (
                host_member_id, title, video_url, video_duration_seconds, video_thumbnail_url,
                video_title, max_participants, is_public, status, start_at, created_at)
            VALUES (
                ?, '테스트 시청방', 'https://example.com/video', 600, 'https://example.com/thumbnail.png',
                '테스트 영상', 10, TRUE, 'LIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            RETURNING id
            """;

    private static final String INSERT_CHAT_SQL = """
            INSERT INTO chats (message_id, member_id, room_id, video_position_seconds, content, created_at)
            VALUES (?, ?, ?, 0, '안녕하세요', CURRENT_TIMESTAMP)
            """;

    private static final String INSERT_REACTION_SQL = """
            INSERT INTO reactions (message_id, room_id, member_id, reaction_type, video_position_seconds, created_at)
            VALUES (?, ?, ?, 'HEART', 0, CURRENT_TIMESTAMP)
            """;

    private static final String INSERT_REPORT_SQL = """
            INSERT INTO reports (room_id, total_participants, chat_count, created_at)
            VALUES (?, 0, 0, CURRENT_TIMESTAMP)
            """;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long memberId;
    private Long roomId;

    @BeforeEach
    void setUp() {
        memberId = jdbcTemplate.queryForObject(INSERT_MEMBER_SQL, Long.class);
        roomId = jdbcTemplate.queryForObject(INSERT_ROOM_SQL, Long.class, memberId);
    }

    @Test
    @DisplayName("messageId가 다른 채팅과 리액션은 저장할 수 있다")
    void saveActivitiesWithDifferentMessageIds() {
        assertThatCode(() -> {
            insertChat("message-1");
            insertChat("message-2");
            insertReaction("message-3");
            insertReaction("message-4");
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("같은 messageId의 채팅은 중복 저장할 수 없다")
    void rejectDuplicateChatMessageId() {
        insertChat("same-message");

        assertThatThrownBy(() -> insertChat("same-message"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("같은 messageId의 리액션은 중복 저장할 수 없다")
    void rejectDuplicateReactionMessageId() {
        insertReaction("same-message");

        assertThatThrownBy(() -> insertReaction("same-message"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("messageId가 없는 채팅은 저장할 수 없다")
    void rejectChatWithoutMessageId() {
        assertThatThrownBy(() -> insertChat(null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("한 시청방의 리포트는 중복 저장할 수 없다")
    void rejectDuplicateReportOfRoom() {
        insertReport();

        assertThatThrownBy(this::insertReport)
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertChat(String messageId) {
        jdbcTemplate.update(INSERT_CHAT_SQL, messageId, memberId, roomId);
    }

    private void insertReaction(String messageId) {
        jdbcTemplate.update(INSERT_REACTION_SQL, messageId, roomId, memberId);
    }

    private void insertReport() {
        jdbcTemplate.update(INSERT_REPORT_SQL, roomId);
    }
}
