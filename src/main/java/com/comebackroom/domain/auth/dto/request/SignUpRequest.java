package com.comebackroom.domain.auth.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SignUpRequest(
        @NotBlank String loginId,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank String emailVerificationToken,
        @NotBlank String nickname,
        @NotBlank String password,
        @NotBlank String passwordConfirm,
        @NotNull @AssertTrue Boolean termsAgreed,
        @NotNull @AssertTrue Boolean privacyAgreed
) {
}
