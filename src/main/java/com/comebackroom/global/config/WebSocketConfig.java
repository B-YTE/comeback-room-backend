package com.comebackroom.global.config;

import com.comebackroom.global.security.config.CorsProperties;
import com.comebackroom.global.websocket.handler.StompErrorHandler;
import com.comebackroom.global.websocket.interceptor.StompAuthChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final String ENDPOINT = "/ws/watching-rooms"; // 클라이언트 접속 경로
    private static final String APPLICATION_DESTINATION_PREFIX = "/pub"; // 클라이언트 -> 서버로 보내는 메시지 접두어
    private static final String BROKER_DESTINATION_PREFIX = "/sub"; // 서버 -> 구독자로 보내는 메시지 접두어

    private static final int TIME_TO_FIRST_MESSAGE_MS = 10_000; // 연결 후 첫 STOMP 프레임을 기다리는 시간(ms, 10초)

    private final CorsProperties corsProperties;
    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;
    private final StompErrorHandler stompErrorHandler;


    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(ENDPOINT) // WebSocket 연결을 맺는 엔드포인트 등록
                .setAllowedOriginPatterns(corsProperties.allowedOrigins().toArray(String[]::new));
        // 허용 목록에 없는 출처의 hand-shake는 거부(403)
        registry.setErrorHandler(stompErrorHandler); // 프레임 처리 실패 시 ERROR 프레임을 만들 핸들러 등록
    }

    // 메시지의 destination에 따라 어디로 보낼지 결정
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // '/pub' - 서버 코드(@MessageMapping)가 처리
        registry.setApplicationDestinationPrefixes(APPLICATION_DESTINATION_PREFIX);
        // '/sub' - 브로커가 구독자 목록 관리 및 구독자에게 메시지 전달
        registry.enableSimpleBroker(BROKER_DESTINATION_PREFIX);
    }

    // 클라이언트 -> 서버로 들어오는 프레임의 통로(clientInboundChannel)에 인터셉터 등록
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        // (새 연결이 맺어질 때) 연결이 열린 뒤 시간 안에 STOMP 프레임을 한 번도 보내지 않은 연결을 닫음
        registration.setTimeToFirstMessage(TIME_TO_FIRST_MESSAGE_MS);
    }

}
