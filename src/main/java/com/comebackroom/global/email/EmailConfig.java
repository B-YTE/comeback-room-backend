package com.comebackroom.global.email;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EmailProperties.class)
public class EmailConfig {

    @Bean
    public EmailSender emailSender(EmailProperties emailProperties) {
        return switch (emailProperties.deliveryMode()) {
            case CONSOLE -> new ConsoleEmailSender();
            case RESEND -> new ResendEmailSender(
                    createResendClient(emailProperties),
                    emailProperties.fromAddress()
            );
        };
    }

    private static RestClient createResendClient(EmailProperties emailProperties) {
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(emailProperties.connectTimeout());
        requestFactory.setReadTimeout(emailProperties.readTimeout());

        return RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(emailProperties.resendBaseUrl().toString())
                .defaultHeader(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + emailProperties.resendApiKey()
                )
                .build();
    }
}
