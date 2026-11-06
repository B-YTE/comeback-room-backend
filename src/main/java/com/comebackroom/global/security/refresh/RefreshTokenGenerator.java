package com.comebackroom.global.security.refresh;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
public class RefreshTokenGenerator {

    private static final int TOKEN_LENGTH_BYTES = 32;

    private final SecureRandom secureRandom;

    public RefreshTokenGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    public String generateToken() {
        byte[] randomBytes = new byte[TOKEN_LENGTH_BYTES];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);
    }
}
