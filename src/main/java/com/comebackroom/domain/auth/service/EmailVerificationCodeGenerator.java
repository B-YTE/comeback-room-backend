package com.comebackroom.domain.auth.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Locale;

@Component
public class EmailVerificationCodeGenerator {

    private static final int CODE_BOUND = 1_000_000;

    private final SecureRandom secureRandom;

    public EmailVerificationCodeGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    public String generateCode() {
        return String.format(Locale.ROOT, "%06d", secureRandom.nextInt(CODE_BOUND));
    }
}
