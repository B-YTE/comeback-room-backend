package com.comebackroom.global.security.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayName("JWT Access Token 발급")
class JwtTokenProviderTest {

    private static final String VALID_SECRET =
            "Y2hhbmdlLW1lLWJlZm9yZS11c2luZy1pbi1wcm9kdWN0aW9uIQ==";
    private static final Duration ACCESS_TOKEN_EXPIRATION = Duration.ofMinutes(30);
    private static final Instant ISSUED_AT = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private JwtTokenProvider jwtTokenProvider;
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties(VALID_SECRET, ACCESS_TOKEN_EXPIRATION);
        JwtConfig jwtConfig = new JwtConfig();
        SecretKey secretKey = jwtConfig.jwtSecretKey(jwtProperties);
        JwtEncoder jwtEncoder = jwtConfig.jwtEncoder(secretKey);
        jwtDecoder = jwtConfig.jwtDecoder(secretKey);
        jwtTokenProvider = new JwtTokenProvider(
                jwtEncoder,
                jwtProperties,
                Clock.fixed(ISSUED_AT, ZoneOffset.UTC)
        );
    }

    @Test
    @DisplayName("회원 ID와 ACCESS 종류 및 만료시간을 포함해 발급한다")
    void createAccessToken() {
        String token = jwtTokenProvider.createAccessToken(1L);

        Jwt jwt = jwtDecoder.decode(token);

        assertAll(
                () -> assertThat(jwt.getSubject()).isEqualTo("1"),
                () -> assertThat(jwt.getClaimAsString("type")).isEqualTo("ACCESS"),
                () -> assertThat(jwt.getIssuedAt()).isEqualTo(ISSUED_AT),
                () -> assertThat(jwt.getExpiresAt()).isEqualTo(ISSUED_AT.plus(ACCESS_TOKEN_EXPIRATION))
        );
    }

    @ParameterizedTest(name = "memberId가 {0}이면 발급할 수 없다")
    @NullSource
    @ValueSource(longs = {0L, -1L})
    @DisplayName("회원 ID가 null 또는 0 이하이면 발급할 수 없다")
    void failToCreateAccessTokenWithInvalidMemberId(Long memberId) {
        assertThatThrownBy(() -> jwtTokenProvider.createAccessToken(memberId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("memberId 값은 1 이상이어야 합니다.");
    }
}
