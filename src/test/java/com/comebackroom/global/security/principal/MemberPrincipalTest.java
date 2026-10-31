package com.comebackroom.global.security.principal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

    @Test
    @DisplayName("회원 ID가 없으면 생성할 수 없다")
    void failToCreateWithoutMemberId() {
        assertThatThrownBy(() -> new MemberPrincipal(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("memberId 값은 필수입니다.");
    }
}
