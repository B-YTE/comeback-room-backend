package com.comebackroom.global.config.database;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Profile("render")
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RenderPostgresProperties.class)
public class RenderPostgresConfig {

    @Bean
    public JdbcConnectionDetails renderJdbcConnectionDetails(
            RenderPostgresProperties renderPostgresProperties
    ) {
        return RenderJdbcConnectionDetails.from(renderPostgresProperties.databaseUrl());
    }
}
