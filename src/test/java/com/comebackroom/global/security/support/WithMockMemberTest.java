package com.comebackroom.global.security.support;

import com.comebackroom.global.security.principal.MemberPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringJUnitConfig(WithMockMemberTest.TestConfig.class)
@DisplayName("Mock 회원 인증")
class WithMockMemberTest {

    private static final long MEMBER_ID = 42L;

    @Test
    @WithMockMember(memberId = MEMBER_ID)
    @DisplayName("지정한 회원 ID로 인증 정보를 생성한다")
    void createAuthenticationWithMemberId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        assertAll(
                () -> assertThat(authentication.isAuthenticated()).isTrue(),
                () -> assertThat(authentication.getCredentials()).isNull(),
                () -> assertThat(authentication.getAuthorities()).isEmpty(),
                () -> assertThat(authentication.getPrincipal())
                        .isInstanceOfSatisfying(MemberPrincipal.class, memberPrincipal ->
                                assertThat(memberPrincipal.memberId()).isEqualTo(MEMBER_ID))
        );
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfig {
    }
}
