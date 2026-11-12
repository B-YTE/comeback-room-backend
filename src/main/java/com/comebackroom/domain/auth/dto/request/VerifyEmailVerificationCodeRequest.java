package com.comebackroom.domain.auth.dto.request;

import com.comebackroom.domain.auth.enums.EmailVerificationPurpose;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VerifyEmailVerificationCodeRequest(
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "올바른 이메일 형식이어야 합니다.")
        @Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
        String email,

        @NotBlank(message = "이메일 인증코드는 필수입니다.")
        @Pattern(regexp = "\\d{6}", message = "이메일 인증코드는 숫자 6자리여야 합니다.")
        String verificationCode,

        @NotNull(message = "이메일 인증 목적은 필수입니다.")
        EmailVerificationPurpose purpose
) {
}
