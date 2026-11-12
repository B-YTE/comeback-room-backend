package com.comebackroom.global.email;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("이메일 발송 설정")
class EmailPropertiesTest {

    @Test
    @DisplayName("로컬 콘솔 모드는 Resend 자격증명 없이 구성할 수 있다")
    void createConsoleProperties() {
        assertThatCode(() -> new EmailProperties(
                EmailDeliveryMode.CONSOLE,
                URI.create("https://api.resend.com"),
                "",
                ""
        )).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Resend 모드는 API Key와 발신 주소가 필요하다")
    void requireResendCredentials() {
        assertThatThrownBy(() -> new EmailProperties(
                EmailDeliveryMode.RESEND,
                URI.create("https://api.resend.com"),
                "",
                ""
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Resend API Key는 필수입니다.");
    }

    @Test
    @DisplayName("Resend API는 HTTPS 주소만 허용한다")
    void requireHttpsResendUrl() {
        assertThatThrownBy(() -> new EmailProperties(
                EmailDeliveryMode.RESEND,
                URI.create("http://api.resend.com"),
                "api-key",
                "auth@example.com"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Resend API Base URL은 HTTPS여야 합니다.");
    }
}
