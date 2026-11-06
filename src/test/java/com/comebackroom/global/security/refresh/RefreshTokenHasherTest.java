package com.comebackroom.global.security.refresh;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayName("Refresh Token 해싱")
class RefreshTokenHasherTest {

    private final RefreshTokenHasher refreshTokenHasher = new RefreshTokenHasher();

    @Test
    @DisplayName("Refresh Token을 SHA-256 해시로 변환한다")
    void hashToken() {
        String tokenHash = refreshTokenHasher.hashToken("refresh-token");

        assertThat(tokenHash)
                .isEqualTo("0eb17643d4e9261163783a420859c92c7d212fa9624106a12b510afbec266120")
                .hasSize(64);
    }

    @Test
    @DisplayName("동일한 Refresh Token은 동일한 해시로 변환된다")
    void hashTokenDeterministically() {
        String firstHash = refreshTokenHasher.hashToken("same-token");
        String secondHash = refreshTokenHasher.hashToken("same-token");

        assertThat(firstHash).isEqualTo(secondHash);
    }

    @Test
    @DisplayName("Refresh Token 값이 없으면 해시할 수 없다")
    void rejectMissingToken() {
        assertAll(
                () -> assertThatThrownBy(
                        () -> refreshTokenHasher.hashToken(null)
                ).isInstanceOf(IllegalArgumentException.class),
                () -> assertThatThrownBy(
                        () -> refreshTokenHasher.hashToken(" ")
                ).isInstanceOf(IllegalArgumentException.class)
        );
    }
}
