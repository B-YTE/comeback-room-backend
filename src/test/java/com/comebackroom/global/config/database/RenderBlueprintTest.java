package com.comebackroom.global.config.database;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayName("Render Blueprint")
class RenderBlueprintTest {

    private Map<String, Object> blueprint;

    @BeforeEach
    void loadBlueprint() throws IOException {
        try (InputStream inputStream = Files.newInputStream(Path.of("render.yaml"))) {
            blueprint = new Yaml().load(inputStream);
        }
    }

    @Test
    @DisplayName("develop 브랜치의 Docker 서비스를 CI 통과 후 배포한다")
    void configureWebServiceDeployment() {
        Map<String, Object> service = firstMap(blueprint, "services");

        assertAll(
                () -> assertThat(service.get("type")).isEqualTo("web"),
                () -> assertThat(service.get("runtime")).isEqualTo("docker"),
                () -> assertThat(service.get("branch")).isEqualTo("develop"),
                () -> assertThat(service.get("autoDeployTrigger")).isEqualTo("checksPass"),
                () -> assertThat(service.get("healthCheckPath")).isEqualTo("/actuator/health")
        );
    }

    @Test
    @DisplayName("데이터베이스와 비밀값을 안전한 참조로 주입한다")
    void configureSecureEnvironmentReferences() {
        Map<String, Object> service = firstMap(blueprint, "services");
        List<Map<String, Object>> environmentVariables = mapList(service, "envVars");
        Map<String, Object> databaseUrl = findByKey(environmentVariables, "DATABASE_URL");
        Map<String, Object> jwtSecret = findByKey(environmentVariables, "JWT_SECRET");
        Map<String, Object> corsOrigins = findByKey(environmentVariables, "CORS_ALLOWED_ORIGINS");

        assertAll(
                () -> assertThat(map(databaseUrl, "fromDatabase"))
                        .containsEntry("name", "comeback-room-postgres-dev")
                        .containsEntry("property", "connectionString"),
                () -> assertThat(jwtSecret)
                        .containsEntry("generateValue", true)
                        .doesNotContainKey("value"),
                () -> assertThat(corsOrigins)
                        .containsEntry("sync", false)
                        .doesNotContainKey("value")
        );
    }

    @Test
    @DisplayName("Web Service와 PostgreSQL을 동일한 리전에 구성한다")
    void configureServicesInSameRegion() {
        Map<String, Object> service = firstMap(blueprint, "services");
        Map<String, Object> database = firstMap(blueprint, "databases");

        assertAll(
                () -> assertThat(service.get("region")).isEqualTo("singapore"),
                () -> assertThat(database.get("region")).isEqualTo(service.get("region")),
                () -> assertThat(database.get("plan")).isEqualTo("free"),
                () -> assertThat(database.get("postgresMajorVersion")).isEqualTo("17")
        );
    }

    private static Map<String, Object> firstMap(Map<String, Object> source, String key) {
        return mapList(source, key).getFirst();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mapList(Map<String, Object> source, String key) {
        return (List<Map<String, Object>>) source.get(key);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Map<String, Object> source, String key) {
        return (Map<String, Object>) source.get(key);
    }

    private static Map<String, Object> findByKey(
            List<Map<String, Object>> environmentVariables,
            String key
    ) {
        return environmentVariables.stream()
                .filter(environmentVariable -> key.equals(environmentVariable.get("key")))
                .findFirst()
                .orElseThrow();
    }
}
