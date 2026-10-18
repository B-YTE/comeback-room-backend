package com.comebackroom.domain.member.entity;

import com.comebackroom.domain.member.enums.Provider;
import com.comebackroom.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "members",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_members_provider_provider_id",
                        columnNames = {"provider", "provider_id"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login_id", length = 50, unique = true)
    private String loginId;

    @Column(name = "password", length = 255)
    private String password;

    @Column(name = "email", length = 255, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 20, nullable = false)
    private Provider provider;

    @Column(name = "provider_id", length = 255)
    private String providerId;

    private Member(String loginId, String password,
                   String email, Provider provider, String providerId) {
        this.loginId = loginId;
        this.password = password;
        this.email = email;
        this.provider = provider;
        this.providerId = providerId;
    }

    public static Member createLocalMember(String loginId, String encodedPassword, String email) {
        validateRequiredField(loginId, "loginId");
        validateRequiredField(encodedPassword, "password");
        validateRequiredField(email, "email");

        return new Member(loginId, encodedPassword, email, Provider.LOCAL, null);
    }

    public static Member createGoogleMember(String email, String providerId) {
        validateRequiredField(email, "email");
        validateRequiredField(providerId, "providerId");

        return new Member(null, null, email, Provider.GOOGLE, providerId);
    }

    private static void validateRequiredField(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 값은 필수입니다.");
        }
    }
}
