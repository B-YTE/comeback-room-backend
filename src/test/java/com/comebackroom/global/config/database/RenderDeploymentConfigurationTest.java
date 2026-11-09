package com.comebackroom.global.config.database;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayName("Render 배포 환경 설정")
class RenderDeploymentConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod", "render"))
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(RenderPostgresConfig.class)
            .withPropertyValues(
                    "PORT=12000",
                    "DATABASE_URL=postgresql://render_user:render_password@db.internal:5432/comebackroom",
                    "CORS_ALLOWED_ORIGINS=https://frontend.example.com"
            );

    private final ApplicationContextRunner localContextRunner = new ApplicationContextRunner()
            .withInitializer(context -> context.getEnvironment().setActiveProfiles("local"))
            .withInitializer(new ConfigDataApplicationContextInitializer());

    @Test
    @DisplayName("Render 포트와 PostgreSQL 연결 설정을 적용한다")
    void applyRenderServerAndDatabaseConfiguration() {
        contextRunner.run(context -> {
            JdbcConnectionDetails connectionDetails = context.getBean(JdbcConnectionDetails.class);

            assertAll(
                    () -> assertThat(context.getEnvironment().getProperty("server.address"))
                            .isEqualTo("0.0.0.0"),
                    () -> assertThat(context.getEnvironment().getProperty("server.port", Integer.class))
                            .isEqualTo(12000),
                    () -> assertThat(connectionDetails.getJdbcUrl())
                            .isEqualTo("jdbc:postgresql://db.internal:5432/comebackroom"),
                    () -> assertThat(connectionDetails.getUsername()).isEqualTo("render_user")
            );
        });
    }

    @Test
    @DisplayName("운영 CORS와 Refresh Token Cookie 정책을 적용한다")
    void applyProductionCorsAndCookieConfiguration() {
        contextRunner.run(context -> assertAll(
                () -> assertThat(context.getEnvironment().getProperty("app.cors.allowed-origins"))
                        .isEqualTo("https://frontend.example.com"),
                () -> assertThat(context.getEnvironment().getProperty(
                        "app.refresh-token.cookie-secure",
                        Boolean.class
                )).isTrue(),
                () -> assertThat(context.getEnvironment().getProperty("app.refresh-token.cookie-same-site"))
                        .isEqualTo("None"),
                () -> assertThat(context.getEnvironment().getProperty("management.endpoint.health.show-details"))
                        .isEqualTo("never")
        ));
    }

    @Test
    @DisplayName("로컬 환경은 HTTP 개발용 CORS와 Cookie 정책을 유지한다")
    void keepLocalCorsAndCookieConfigurationSeparate() {
        localContextRunner.run(context -> assertAll(
                () -> assertThat(context.getEnvironment().getProperty("app.cors.allowed-origins"))
                        .isEqualTo("http://localhost:5173"),
                () -> assertThat(context.getEnvironment().getProperty(
                        "app.refresh-token.cookie-secure",
                        Boolean.class
                )).isFalse(),
                () -> assertThat(context.getEnvironment().getProperty("app.refresh-token.cookie-same-site"))
                        .isEqualTo("Lax")
        ));
    }
}
