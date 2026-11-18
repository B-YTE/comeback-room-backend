package com.comebackroom.domain.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EmailVerificationProperties.class)
public class EmailVerificationConfig {
}
