package com.comebackroom.global.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(
        List<String> allowedOrigins
) {
    public CorsProperties {
        allowedOrigins = List.copyOf(allowedOrigins);
    }
}
