package com.comebackroom.global.security.refresh;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RefreshTokenProperties.class)
public class RefreshTokenConfig {

    @Bean
    public SecureRandom refreshTokenSecureRandom() {
        return new SecureRandom();
    }
}
