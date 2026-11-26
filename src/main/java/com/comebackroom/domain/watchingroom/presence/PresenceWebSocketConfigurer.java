package com.comebackroom.domain.watchingroom.presence;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@RequiredArgsConstructor
public class PresenceWebSocketConfigurer implements WebSocketMessageBrokerConfigurer {

    private final PresenceHeartbeatInterceptor presenceHeartbeatInterceptor;

    // 서버 프레임이 지나는 채널에 인터셉터 추가
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(presenceHeartbeatInterceptor);
    }
}
