package com.comebackroom.global.security.config;

import com.comebackroom.global.security.handler.SecurityAccessDeniedHandler;
import com.comebackroom.global.security.handler.SecurityAuthenticationEntryPoint;
import com.comebackroom.global.security.handler.SecurityErrorResponseWriter;
import com.comebackroom.global.security.jwt.JwtConfig;
import com.comebackroom.global.security.jwt.JwtTokenProvider;
import com.comebackroom.global.security.jwt.MemberJwtAuthenticationConverter;
import com.comebackroom.global.security.principal.MemberPrincipal;
import com.comebackroom.global.security.support.WithMockMember;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({
        SecurityConfig.class,
        JwtConfig.class,
        JwtTokenProvider.class,
        MemberJwtAuthenticationConverter.class,
        SecurityErrorResponseWriter.class,
        SecurityAuthenticationEntryPoint.class,
        SecurityAccessDeniedHandler.class,
        SecurityConfigTest.MethodSecurityTestConfiguration.class,
        SecurityConfigTest.TestController.class
})
@WebMvcTest(
        controllers = SecurityConfigTest.TestController.class,
        properties = {
                "app.cors.allowed-origins=http://localhost:5173",
                "app.jwt.secret=Y2hhbmdlLW1lLWJlZm9yZS11c2luZy1pbi1wcm9kdWN0aW9uIQ==",
                "app.jwt.access-token-expiration=30m"
        }
)
@DisplayName("Spring Security 기본 설정")
class SecurityConfigTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";
    private static final String DISALLOWED_ORIGIN = "https://malicious.example";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private SecurityAccessDeniedHandler accessDeniedHandler;

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

    @ParameterizedTest(name = "명세에 없는 {0}은 인증 없이는 접근할 수 없다")
    @ValueSource(strings = {
            "/api/v1/auth/email/unexpected",
            "/api/v1/auth/password/unexpected"
    })
    void rejectUndeclaredAuthPostEndpointWithoutAuthentication(String endpoint) throws Exception {
        mockMvc.perform(post(endpoint))
                .andExpect(status().isUnauthorized());
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
    @DisplayName("인증이 없으면 공통 401 응답을 반환한다")
    void returnCommonUnauthorizedResponseWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/profiles/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."))
                .andExpect(jsonPath("$.path").value("/api/v1/profiles/me"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("유효한 Access Token으로 회원 인증 정보를 설정한다")
    void authenticateMemberWithValidAccessToken() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken(7L);

        mockMvc.perform(get("/api/v1/profiles/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(content().string("7"));
    }

    @ParameterizedTest(name = "유효하지 않은 토큰 사유: {0}")
    @ValueSource(strings = {
            "not-a-jwt",
            "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.invalid-signature"
    })
    @DisplayName("형식 또는 서명이 잘못된 Access Token을 거부한다")
    void rejectMalformedOrForgedAccessToken(String accessToken) throws Exception {
        mockMvc.perform(get("/api/v1/profiles/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("만료된 Access Token을 거부한다")
    void rejectExpiredAccessToken() throws Exception {
        Instant now = Instant.now();
        String accessToken = createToken("1", "ACCESS", now.minusSeconds(300), now.minusSeconds(120));

        mockMvc.perform(get("/api/v1/profiles/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @ParameterizedTest(name = "subject={0}, type={1} 토큰은 거부한다")
    @CsvSource({
            "0, ACCESS",
            "abc, ACCESS",
            "1, REFRESH"
    })
    @DisplayName("회원 식별자 또는 토큰 종류가 올바르지 않으면 거부한다")
    void rejectInvalidMemberIdOrTokenType(String subject, String tokenType) throws Exception {
        Instant now = Instant.now();
        String accessToken = createToken(subject, tokenType, now, now.plusSeconds(300));

        mockMvc.perform(get("/api/v1/profiles/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("인증됐지만 권한이 부족하면 공통 403 응답을 반환한다")
    void returnCommonForbiddenResponseWithoutRequiredAuthority() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken(1L);

        mockMvc.perform(get("/test/admin")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("접근 권한이 없습니다."))
                .andExpect(jsonPath("$.path").value("/test/admin"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Security Filter의 인가 실패도 공통 403 응답을 반환한다")
    void returnCommonForbiddenResponseFromSecurityFilter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test/filter-denied");
        MockHttpServletResponse response = new MockHttpServletResponse();

        accessDeniedHandler.handle(request, response, new AccessDeniedException("denied"));

        assertAll(
                () -> assertThat(response.getStatus()).isEqualTo(403),
                () -> assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE),
                () -> assertThat(response.getContentAsString())
                        .contains("\"status\":403")
                        .contains("\"code\":\"FORBIDDEN\"")
                        .contains("\"message\":\"접근 권한이 없습니다.\"")
                        .contains("\"path\":\"/test/filter-denied\"")
                        .contains("\"timestamp\"")
        );
    }

    @Test
    @DisplayName("공개 경로라도 허용한 HTTP 메서드가 아니면 인증이 필요하다")
    void rejectUnlistedMethodWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/auth/sign-up"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockMember(memberId = 1L)
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

    private String createToken(
            String subject,
            String tokenType,
            Instant issuedAt,
            Instant expiresAt
    ) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256)
                .type("JWT")
                .build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("type", tokenType)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityTestConfiguration {
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
                "/api/v1/auth/sign-up",
                "/actuator/health"
        })
        void getEndpoint() {
        }

        @GetMapping("/api/v1/profiles/me")
        Long getProfile(@AuthenticationPrincipal MemberPrincipal memberPrincipal) {
            return memberPrincipal.memberId();
        }

        @PreAuthorize("hasAuthority('ADMIN')")
        @GetMapping("/test/admin")
        void getAdminEndpoint() {
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
