package com.comebackroom.domain.member.entity;

import com.comebackroom.domain.member.enums.Provider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayName("Member 엔티티")
class MemberTest {

    private static final String LOGIN_ID = "zin123";
    private static final String ENCODED_PASSWORD = "{bcrypt}$2a$10$encodedPassword";
    private static final String EMAIL = "zin@example.com";
    private static final String GOOGLE_PROVIDER_ID = "google-subject-123";

    @Test
    @DisplayName("LOCAL 회원을 생성한다")
    void createLocalMember() {
        Member member = Member.createLocalMember(LOGIN_ID, ENCODED_PASSWORD, EMAIL);

        assertAll(
                () -> assertThat(member.getLoginId()).isEqualTo(LOGIN_ID),
                () -> assertThat(member.getPassword()).isEqualTo(ENCODED_PASSWORD),
                () -> assertThat(member.getEmail()).isEqualTo(EMAIL),
                () -> assertThat(member.getProvider()).isEqualTo(Provider.LOCAL),
                () -> assertThat(member.getProviderId()).isNull()
        );
    }

    @Test
    @DisplayName("GOOGLE 회원을 생성한다")
    void createGoogleMember() {
        Member member = Member.createGoogleMember(EMAIL, GOOGLE_PROVIDER_ID);

        assertAll(
                () -> assertThat(member.getLoginId()).isNull(),
                () -> assertThat(member.getPassword()).isNull(),
                () -> assertThat(member.getEmail()).isEqualTo(EMAIL),
                () -> assertThat(member.getProvider()).isEqualTo(Provider.GOOGLE),
                () -> assertThat(member.getProviderId()).isEqualTo(GOOGLE_PROVIDER_ID)
        );
    }

    @Test
    @DisplayName("LOCAL 회원 필수값이 없으면 생성할 수 없다")
    void failToCreateLocalMemberWithoutRequiredField() {
        assertAll(
                () -> assertThatThrownBy(
                        () -> Member.createLocalMember(null, ENCODED_PASSWORD, EMAIL)
                ).isInstanceOf(IllegalArgumentException.class),
                () -> assertThatThrownBy(
                        () -> Member.createLocalMember(LOGIN_ID, " ", EMAIL)
                ).isInstanceOf(IllegalArgumentException.class),
                () -> assertThatThrownBy(
                        () -> Member.createLocalMember(LOGIN_ID, ENCODED_PASSWORD, "")
                ).isInstanceOf(IllegalArgumentException.class)
        );
    }

    @Test
    @DisplayName("GOOGLE 회원 필수값이 없으면 생성할 수 없다")
    void failToCreateGoogleMemberWithoutRequiredField() {
        assertAll(
                () -> assertThatThrownBy(
                        () -> Member.createGoogleMember(null, GOOGLE_PROVIDER_ID)
                ).isInstanceOf(IllegalArgumentException.class),
                () -> assertThatThrownBy(
                        () -> Member.createGoogleMember(EMAIL, " ")
                ).isInstanceOf(IllegalArgumentException.class)
        );
    }
}
