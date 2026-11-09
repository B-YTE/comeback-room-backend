package com.comebackroom.global.config.database;

import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;

import java.net.URI;

final class RenderJdbcConnectionDetails implements JdbcConnectionDetails {

    private static final String POSTGRESQL_SCHEME = "postgresql";
    private static final int DEFAULT_POSTGRESQL_PORT = 5432;

    private final String jdbcUrl;
    private final String username;
    private final String password;

    private RenderJdbcConnectionDetails(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getJdbcUrl() {
        return jdbcUrl;
    }

    static RenderJdbcConnectionDetails from(URI databaseUrl) {
        validateDatabaseUrl(databaseUrl);

        String[] credentials = parseCredentials(databaseUrl.getUserInfo());
        String jdbcUrl = createJdbcUrl(databaseUrl);
        return new RenderJdbcConnectionDetails(jdbcUrl, credentials[0], credentials[1]);
    }

    private static void validateDatabaseUrl(URI databaseUrl) {
        if (!POSTGRESQL_SCHEME.equalsIgnoreCase(databaseUrl.getScheme())) {
            throw new IllegalArgumentException("Render PostgreSQL 연결 URL 형식이 올바르지 않습니다.");
        }
        if (databaseUrl.getHost() == null || databaseUrl.getHost().isBlank()) {
            throw new IllegalArgumentException("Render PostgreSQL 호스트는 필수입니다.");
        }
        if (databaseUrl.getRawPath() == null || databaseUrl.getRawPath().length() <= 1) {
            throw new IllegalArgumentException("Render PostgreSQL 데이터베이스 이름은 필수입니다.");
        }
        if (databaseUrl.getFragment() != null) {
            throw new IllegalArgumentException("Render PostgreSQL 연결 URL에 fragment를 사용할 수 없습니다.");
        }
    }

    private static String[] parseCredentials(String userInfo) {
        if (userInfo == null) {
            throw new IllegalArgumentException("Render PostgreSQL 사용자 정보는 필수입니다.");
        }

        int separatorIndex = userInfo.indexOf(':');
        if (separatorIndex <= 0 || separatorIndex == userInfo.length() - 1) {
            throw new IllegalArgumentException("Render PostgreSQL 사용자 정보 형식이 올바르지 않습니다.");
        }

        return new String[]{
                userInfo.substring(0, separatorIndex),
                userInfo.substring(separatorIndex + 1)
        };
    }

    private static String createJdbcUrl(URI databaseUrl) {
        int port = databaseUrl.getPort() == -1
                ? DEFAULT_POSTGRESQL_PORT
                : databaseUrl.getPort();
        String host = databaseUrl.getHost().contains(":")
                ? "[" + databaseUrl.getHost() + "]"
                : databaseUrl.getHost();
        String query = databaseUrl.getRawQuery() == null
                ? ""
                : "?" + databaseUrl.getRawQuery();

        return "jdbc:postgresql://" + host + ":" + port + databaseUrl.getRawPath() + query;
    }
}
