package com.comebackroom.global.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("Resend 이메일 발송")
class ResendEmailSenderTest {

    private static final String BASE_URL = "https://api.resend.com";
    private static final String API_KEY = "resend-api-key";
    private static final String FROM_ADDRESS = "Comeback Room <auth@example.com>";

    private MockRestServiceServer server;
    private ResendEmailSender emailSender;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + API_KEY);
        server = MockRestServiceServer.bindTo(builder).build();
        emailSender = new ResendEmailSender(builder.build(), FROM_ADDRESS);
    }

    @Test
    @DisplayName("API Key와 멱등성 키를 포함해 Resend HTTPS API로 이메일을 발송한다")
    void send() {
        server.expect(once(), requestTo(BASE_URL + "/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + API_KEY))
                .andExpect(header("Idempotency-Key", "email-verification/hash"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "from": "Comeback Room <auth@example.com>",
                          "to": ["zin@example.com"],
                          "subject": "[Comeback Room] 이메일 인증코드",
                          "text": "인증코드는 123456입니다."
                        }
                        """))
                .andRespond(withSuccess("{\"id\":\"email-id\"}", MediaType.APPLICATION_JSON));

        assertThatCode(() -> emailSender.send(createEmailMessage()))
                .doesNotThrowAnyException();
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {403, 429, 500, 503})
    @DisplayName("Resend API가 실패하면 이메일 발송 예외로 변환한다")
    void failToSend(int status) {
        server.expect(once(), requestTo(BASE_URL + "/emails"))
                .andRespond(withStatus(HttpStatus.valueOf(status)));

        assertThatThrownBy(() -> emailSender.send(createEmailMessage()))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessage("이메일 발송에 실패했습니다.");
        server.verify();
    }

    @Test
    @DisplayName("Resend 타임아웃도 이메일 발송 예외로 변환한다")
    void failToSendOnTimeout() {
        server.expect(once(), requestTo(BASE_URL + "/emails"))
                .andRespond(withException(new SocketTimeoutException("응답 시간 초과")));

        assertThatThrownBy(() -> emailSender.send(createEmailMessage()))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessage("이메일 발송에 실패했습니다.");
        server.verify();
    }

    private static EmailMessage createEmailMessage() {
        return new EmailMessage(
                "zin@example.com",
                "[Comeback Room] 이메일 인증코드",
                "인증코드는 123456입니다.",
                "email-verification/hash"
        );
    }
}
