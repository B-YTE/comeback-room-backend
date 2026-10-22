package com.comebackroom.global.security.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({SecurityConfig.class, SecurityConfigTest.TestController.class})
@WebMvcTest(
        controllers = SecurityConfigTest.TestController.class,
        properties = "app.cors.allowed-origins=http://localhost:5173"
)
@DisplayName("Spring Security 기본 설정")
class SecurityConfigTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";
    private static final String DISALLOWED_ORIGIN = "https://malicious.example";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @ParameterizedTest(name = "{0}은 인증 없이 POST 요청할 수 있다")
    @ValueSource(strings = {
            "/api/v1/auth/sign-up",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/email/send",
            "/api/v1/auth/email/verify",
            "/api/v1/auth/find-id",
            "/api/v1/auth/password/reset-request",
            "/api/v1/auth/password/reset"
    })
    void permitPublicPostEndpoint(String endpoint) throws Exception {
        mockMvc.perform(post(endpoint))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Google OAuth2 시작 경로는 인증 없이 접근할 수 있다")
    void permitGoogleOAuth2Endpoint() throws Exception {
        mockMvc.perform(get("/api/v1/oauth2/authorization/google"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("헬스 체크 경로는 인증 없이 접근할 수 있다")
    void permitHealthEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @ParameterizedTest(name = "{0} {1}은 인증 없이는 접근할 수 없다")
    @CsvSource({
            "GET, /api/v1/profiles/me",
            "PATCH, /api/v1/profiles/me/nickname",
            "POST, /api/v1/profiles/me/profile-image/presigned-url",
            "PATCH, /api/v1/profiles/me/profile-image",
            "DELETE, /api/v1/members/me",
            "POST, /api/v1/auth/logout"
    })
    void rejectProtectedEndpointWithoutAuthentication(String method, String endpoint) throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf(method), endpoint))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("공개 경로라도 허용한 HTTP 메서드가 아니면 인증이 필요하다")
    void rejectUnlistedMethodWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/auth/sign-up"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    @DisplayName("인증된 사용자는 보호 경로에 접근할 수 있다")
    void permitProtectedEndpointWithAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/profiles/me"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("허용된 Origin의 CORS 사전 요청을 허용한다")
    void permitCorsPreflightFromAllowedOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/profiles/me")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, HttpHeaders.AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    @DisplayName("허용되지 않은 Origin의 CORS 사전 요청을 거부한다")
    void rejectCorsPreflightFromDisallowedOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/profiles/me")
                        .header(HttpHeaders.ORIGIN, DISALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("비밀번호는 BCrypt로 단방향 암호화한다")
    void encodePasswordWithBCrypt() {
        String rawPassword = "password123!";

        String encodedPassword = passwordEncoder.encode(rawPassword);

        assertThat(encodedPassword).startsWith("$2");
        assertThat(passwordEncoder.matches(rawPassword, encodedPassword)).isTrue();
        assertThat(encodedPassword).isNotEqualTo(rawPassword);
    }

    @RestController
    public static class TestController {

        @PostMapping({
                "/api/v1/auth/sign-up",
                "/api/v1/auth/login",
                "/api/v1/auth/refresh",
                "/api/v1/auth/email/send",
                "/api/v1/auth/email/verify",
                "/api/v1/auth/find-id",
                "/api/v1/auth/password/reset-request",
                "/api/v1/auth/password/reset",
                "/api/v1/auth/logout",
                "/api/v1/profiles/me/profile-image/presigned-url"
        })
        void postEndpoint() {
        }

        @GetMapping({
                "/api/v1/oauth2/authorization/google",
                "/api/v1/profiles/me",
                "/api/v1/auth/sign-up",
                "/actuator/health"
        })
        void getEndpoint() {
        }

        @PatchMapping({
                "/api/v1/profiles/me/nickname",
                "/api/v1/profiles/me/profile-image"
        })
        void patchEndpoint() {
        }

        @DeleteMapping("/api/v1/members/me")
        void deleteEndpoint() {
        }
    }
}
