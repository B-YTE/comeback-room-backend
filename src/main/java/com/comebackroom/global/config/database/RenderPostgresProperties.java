package com.comebackroom.global.config.database;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.Objects;

@ConfigurationProperties(prefix = "app.render")
public record RenderPostgresProperties(
        URI databaseUrl
) {

    public RenderPostgresProperties {
        Objects.requireNonNull(databaseUrl, "Render PostgreSQL 연결 URL은 필수입니다.");
    }
}
