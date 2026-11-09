package com.comebackroom.global.config.database;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayName("Render 배포 환경 설정")
class RenderDeploymentConfigurationTest {

    private final ApplicationContextRunner renderDevContextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withPropertyValues(
                    "spring.profiles.active=dev,render",
                    "PORT=12000",
                    "DB_HOST=db.internal",
                    "DB_PORT=5432",
                    "DB_NAME=comebackroom",
                    "DB_USER=render_user",
                    "DB_PASSWORD=render_password",
                    "CORS_ALLOWED_ORIGINS=https://frontend.example.com"
            );

    private final ApplicationContextRunner productionContextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withPropertyValues(
                    "spring.profiles.active=prod",
                    "DB_URL=jdbc:postgresql://production.internal:5432/comebackroom",
                    "DB_USER=production_user",
                    "DB_PASSWORD=production_password",
                    "CORS_ALLOWED_ORIGINS=https://www.example.com"
            );

    private final ApplicationContextRunner localContextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withPropertyValues("spring.profiles.active=local");

    @Test
    @DisplayName("Render 포트와 PostgreSQL 연결 설정을 적용한다")
    void applyRenderServerAndDatabaseConfiguration() {
        renderDevContextRunner.run(context -> assertAll(
                () -> assertThat(context.getEnvironment().getProperty("server.address"))
                        .isEqualTo("0.0.0.0"),
                () -> assertThat(context.getEnvironment().getProperty("server.port", Integer.class))
                        .isEqualTo(12000),
                () -> assertThat(context.getEnvironment().getProperty("spring.datasource.url"))
                        .isEqualTo("jdbc:postgresql://db.internal:5432/comebackroom"),
                () -> assertThat(context.getEnvironment().getProperty("spring.datasource.username"))
                        .isEqualTo("render_user"),
                () -> assertThat(context.getEnvironment().getProperty("spring.datasource.password"))
                        .isEqualTo("render_password")
        ));
    }

    @Test
    @DisplayName("Render 개발 서버 CORS와 Refresh Token Cookie 정책을 적용한다")
    void applyRenderDevelopmentCorsAndCookieConfiguration() {
        renderDevContextRunner.run(context -> assertAll(
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
    @DisplayName("운영 프로필의 PostgreSQL 연결 설정을 유지한다")
    void keepProductionDatabaseConfiguration() {
        productionContextRunner.run(context -> assertAll(
                () -> assertThat(context.getEnvironment().getProperty("spring.datasource.url"))
                        .isEqualTo("jdbc:postgresql://production.internal:5432/comebackroom"),
                () -> assertThat(context.getEnvironment().getProperty("spring.datasource.username"))
                        .isEqualTo("production_user"),
                () -> assertThat(context.getEnvironment().getProperty("spring.datasource.password"))
                        .isEqualTo("production_password")
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
