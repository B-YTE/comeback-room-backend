package com.comebackroom.global.email;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

public class ResendEmailSender implements EmailSender {

    private static final String EMAILS_PATH = "/emails";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final RestClient restClient;
    private final String fromAddress;

    public ResendEmailSender(RestClient restClient, String fromAddress) {
        this.restClient = restClient;
        this.fromAddress = fromAddress;
    }

    @Override
    public void send(EmailMessage emailMessage) {
        ResendEmailRequest request = new ResendEmailRequest(
                fromAddress,
                List.of(emailMessage.recipient()),
                emailMessage.subject(),
                emailMessage.text()
        );

        try {
            restClient.post()
                    .uri(EMAILS_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(IDEMPOTENCY_KEY_HEADER, emailMessage.idempotencyKey())
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new EmailDeliveryException("이메일 발송에 실패했습니다.", e);
        }
    }

    private record ResendEmailRequest(
            String from,
            List<String> to,
            String subject,
            String text
    ) {
    }
}
