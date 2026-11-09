package com.comebackroom.global.config.database;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayName("Render PostgreSQL JDBC 연결 설정")
class RenderJdbcConnectionDetailsTest {

    @Test
    @DisplayName("Render PostgreSQL URL을 JDBC 연결 정보로 변환한다")
    void convertRenderDatabaseUrl() {
        RenderJdbcConnectionDetails connectionDetails = RenderJdbcConnectionDetails.from(
                URI.create("postgresql://render_user:render_password@db.internal:5432/comebackroom")
        );

        assertAll(
                () -> assertThat(connectionDetails.getJdbcUrl())
                        .isEqualTo("jdbc:postgresql://db.internal:5432/comebackroom"),
                () -> assertThat(connectionDetails.getUsername()).isEqualTo("render_user"),
                () -> assertThat(connectionDetails.getPassword()).isEqualTo("render_password")
        );
    }

    @Test
    @DisplayName("포트가 없으면 PostgreSQL 기본 포트를 사용하고 쿼리를 유지한다")
    void useDefaultPortAndPreserveQuery() {
        RenderJdbcConnectionDetails connectionDetails = RenderJdbcConnectionDetails.from(
                URI.create("postgresql://render_user:render_password@db.internal/comebackroom?sslmode=require")
        );

        assertThat(connectionDetails.getJdbcUrl())
                .isEqualTo("jdbc:postgresql://db.internal:5432/comebackroom?sslmode=require");
    }

    @Test
    @DisplayName("PostgreSQL이 아닌 URL은 거부한다")
    void rejectUnsupportedScheme() {
        assertThatThrownBy(
                () -> RenderJdbcConnectionDetails.from(
                        URI.create("mysql://render_user:render_password@db.internal/comebackroom")
                )
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Render PostgreSQL 연결 URL 형식이 올바르지 않습니다.");
    }

    @Test
    @DisplayName("사용자 정보나 데이터베이스 이름이 없는 URL은 거부한다")
    void rejectIncompleteDatabaseUrl() {
        assertAll(
                () -> assertThatThrownBy(
                        () -> RenderJdbcConnectionDetails.from(
                                URI.create("postgresql://db.internal/comebackroom")
                        )
                ).isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("Render PostgreSQL 사용자 정보는 필수입니다."),
                () -> assertThatThrownBy(
                        () -> RenderJdbcConnectionDetails.from(
                                URI.create("postgresql://render_user:render_password@db.internal")
                        )
                ).isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("Render PostgreSQL 데이터베이스 이름은 필수입니다.")
        );
    }
}
