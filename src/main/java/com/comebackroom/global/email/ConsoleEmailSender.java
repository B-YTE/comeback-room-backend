package com.comebackroom.global.email;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConsoleEmailSender implements EmailSender {

    @Override
    public void send(EmailMessage emailMessage) {
        log.info(
                "로컬 이메일 발송 recipient={} subject={} body={}",
                emailMessage.recipient(),
                emailMessage.subject(),
                emailMessage.text()
        );
    }
}
