package com.example.payment.chat.infrastructure.websocket;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * 상담 메시지를 실시간으로 전달하는 STOMP over WebSocket 설정.
 * 메시지 보내기는 HTTP API가 맡고, WebSocket은 저장된 메시지를 상대에게 알리는 데만 쓴다.
 */
@Configuration
@EnableWebSocketMessageBroker
public class ChatWebSocketConfiguration implements WebSocketMessageBrokerConfigurer {
    public static final String ENDPOINT = "/ws";
    /** 고객이 구독하는 주소. Spring이 연결한 회원에게만 전달한다. */
    public static final String CUSTOMER_SUBSCRIPTION = "/user/queue/chat";
    /** 고객 구독 주소에서 /user를 뺀 주소. 서버가 회원 ID를 붙여 보낼 때 쓴다. */
    public static final String CUSTOMER_QUEUE = "/queue/chat";
    /** 쇼핑몰 관리자가 구독하는 주소. 모든 상담방의 새 메시지가 온다. */
    public static final String ADMIN_TOPIC = "/topic/admin/chat";

    private final ChatSocketAuthorization authorization;
    private TaskScheduler heartbeatScheduler;

    public ChatWebSocketConfiguration(ChatSocketAuthorization authorization) {
        this.authorization = authorization;
    }

    // 끊긴 연결을 알아채도록 10초마다 heartbeat를 주고받는다. 스케줄러는 이 설정이 만드는 빈이라 늦게 주입한다.
    @Autowired
    void setHeartbeatScheduler(@Lazy @Qualifier("messageBrokerTaskScheduler") TaskScheduler heartbeatScheduler) {
        this.heartbeatScheduler = heartbeatScheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(ENDPOINT);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[] {10_000, 10_000})
                .setTaskScheduler(heartbeatScheduler);
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authorization);
    }
}
