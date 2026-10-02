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
 * Spring이 이 클래스를 찾아 브로커를 만드는 순서는 docs/websocket-configuration.md에 있다.
 */
@Configuration
@EnableWebSocketMessageBroker
public class ChatWebSocketConfiguration implements WebSocketMessageBrokerConfigurer {
    public static final String ENDPOINT = "/ws";                            // 브라우저가 WebSocket으로 연결하는 주소
    public static final String CUSTOMER_SUBSCRIPTION = "/user/queue/chat";  // 고객이 구독하는 주소. Spring이 연결한 회원에게만 전달한다
    public static final String CUSTOMER_QUEUE = "/queue/chat";              // 고객 구독 주소에서 /user를 뺀 주소. 서버가 회원 ID를 붙여 보낼 때 쓴다
    public static final String ADMIN_TOPIC = "/topic/admin/chat";           // 쇼핑몰 관리자가 구독하는 주소. 모든 상담방의 새 메시지가 온다

    private final ChatSocketAuthorization authorization;  // 브라우저가 보낸 STOMP 프레임을 검사한다. CONNECT는 토큰 확인, SUBSCRIBE는 구독 권한 확인, SEND는 거부
    private TaskScheduler heartbeatScheduler;              // 10초마다 heartbeat를 주고받는 일정을 돌린다. 브로커가 만든 스케줄러를 받아 쓴다

    public ChatWebSocketConfiguration(ChatSocketAuthorization authorization) {
        this.authorization = authorization;
    }

    // 끊긴 연결을 알아채도록 10초마다 heartbeat를 주고받는다. 스케줄러는 이 설정이 만드는 빈이라 늦게 주입한다.
    @Autowired  // 컨테이너가 이 클래스를 객체로 만든 뒤, @Autowired가 붙은 이 메서드를 자동으로 호출하고 매개변수에는 빈(Bean)을 골라 넣는다
    void setHeartbeatScheduler(@Lazy @Qualifier("messageBrokerTaskScheduler") TaskScheduler heartbeatScheduler) {
        this.heartbeatScheduler = heartbeatScheduler;
    }

    // 브라우저가 WebSocket으로 연결할 주소(/ws)를 등록한다. 엔진 제작자가 접속 주소 연결 부품을 만들 때 호출한다.
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(ENDPOINT);
    }

    // 메모리 브로커를 켜고 /topic, /queue 주소를 맡긴다. 10초 heartbeat와 회원별 주소 접두사 /user도 정한다.
    // 엔진 제작자가 브로커 부품을 만들 때 호출한다.
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[] {10_000, 10_000})
                .setTaskScheduler(heartbeatScheduler);
        registry.setUserDestinationPrefix("/user");
    }

    // 브라우저에서 들어오는 STOMP 프레임을 ChatSocketAuthorization이 먼저 검사하게 한다.
    // 엔진 제작자가 들어오는 메시지 채널을 만들 때 호출한다.
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authorization);
    }
}
