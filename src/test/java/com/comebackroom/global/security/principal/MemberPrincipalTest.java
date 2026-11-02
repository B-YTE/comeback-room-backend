package com.comebackroom.global.security.principal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayName("MemberPrincipal")
class MemberPrincipalTest {

    private static final Long MEMBER_ID = 1L;

    @Test
    @DisplayName("회원 ID를 인증 사용자 식별자로 제공한다")
    void provideMemberIdAsPrincipalName() {
        MemberPrincipal memberPrincipal = new MemberPrincipal(MEMBER_ID);

        assertAll(
                () -> assertThat(memberPrincipal.memberId()).isEqualTo(MEMBER_ID),
                () -> assertThat(memberPrincipal.getName()).isEqualTo(MEMBER_ID.toString())
        );
    }

    @ParameterizedTest(name = "memberId가 {0}이면 생성할 수 없다")
    @NullSource
    @ValueSource(longs = {0L, -1L})
    @DisplayName("회원 ID가 null 또는 0 이하이면 생성할 수 없다")
    void failToCreateWithInvalidMemberId(Long memberId) {
        assertThatThrownBy(() -> new MemberPrincipal(memberId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("memberId 값은 1 이상이어야 합니다.");
    }
}
