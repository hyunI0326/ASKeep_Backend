package com.GDGoCSMU.ASKeep.global.websocket;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * 실시간 알림 (STOMP over WebSocket, 실시간 알림 명세 1장)
 * - 연결: /ws   (프론트: @stomp/stompjs, brokerURL ws://localhost:8080/ws)
 * - 구독: /topic/sessions/{sessionId}
 * - 받기 전용: 클라이언트 → 서버 메시지(SEND)는 받지 않는다
 * - 인증: CONNECT 프레임의 Authorization 헤더 → StompAuthChannelInterceptor
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    public static final String ENDPOINT = "/ws";
    public static final String SESSION_TOPIC_PREFIX = "/topic/sessions/";
    private static final long HEARTBEAT_MILLIS = 10_000;

    private final String[] allowedOrigins;
    private final StompAuthChannelInterceptor authInterceptor;
    private TaskScheduler heartbeatScheduler;

    public WebSocketConfig(
            @Value("${askeep.cors.allowed-origins:http://localhost:3000,http://localhost:5173}") String origins,
            StompAuthChannelInterceptor authInterceptor) {
        this.allowedOrigins = origins.split(",");
        this.authInterceptor = authInterceptor;
    }

    /** heart-beat를 보내려면 스케줄러가 필요하다 (스프링이 만들어 두는 브로커용 스케줄러 사용) */
    @Autowired
    public void setHeartbeatScheduler(@Lazy @Qualifier("messageBrokerTaskScheduler") TaskScheduler scheduler) {
        this.heartbeatScheduler = scheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(ENDPOINT).setAllowedOriginPatterns(allowedOrigins);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic")
                .setHeartbeatValue(new long[]{HEARTBEAT_MILLIS, HEARTBEAT_MILLIS})
                .setTaskScheduler(heartbeatScheduler);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }
}
