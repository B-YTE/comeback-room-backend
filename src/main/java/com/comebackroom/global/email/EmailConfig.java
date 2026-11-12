package com.comebackroom.global.email;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EmailProperties.class)
public class EmailConfig {

    @Bean
    public EmailSender emailSender(EmailProperties emailProperties) {
        return switch (emailProperties.deliveryMode()) {
            case CONSOLE -> new ConsoleEmailSender();
            case RESEND -> new ResendEmailSender(
                    RestClient.builder()
                            .baseUrl(emailProperties.resendBaseUrl().toString())
                            .defaultHeader(
                                    HttpHeaders.AUTHORIZATION,
                                    "Bearer " + emailProperties.resendApiKey()
                            )
                            .build(),
                    emailProperties.fromAddress()
            );
        };
    }
}
