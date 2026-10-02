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
 * 상담 알림을 위한 WebSocket 연결과, 그 위에서 쓰는 STOMP를 함께 설정한다.
 * 브라우저가 연결할 입구(/ws), 구독할 채널 이름, 메시지를 나눠 주는 브로커, 들어오는 프레임 검사를 여기서 정한다.
 * 메시지 보내기는 HTTP API가 맡고, WebSocket은 저장된 메시지를 상대 화면에 알리는 데만 쓴다.
 * 아래 메서드는 우리 코드가 부르지 않는다. 앱이 시작될 때 Spring이 WebSocket 부품을 만들면서 한 번씩 부른다.
 * 그 순서는 docs/websocket-configuration.md에 있다.
 */
@Configuration
@EnableWebSocketMessageBroker  // WebSocket, STOMP, 메시지 브로커를 한꺼번에 켠다. STOMP 없이 WebSocket만 쓰려면 @EnableWebSocket을 쓴다
public class ChatWebSocketConfiguration implements WebSocketMessageBrokerConfigurer {
    public static final String ENDPOINT = "/ws";                            // WebSocket 연결 입구. 브라우저가 ws://호스트/ws로 연결을 열고, 아래 채널은 모두 이 연결 안에서 쓴다
    public static final String CUSTOMER_SUBSCRIPTION = "/user/queue/chat";  // 고객 브라우저가 구독하는 채널. 모든 고객이 같은 이름을 구독하지만 각자 자기 메시지만 받는다
    public static final String CUSTOMER_QUEUE = "/queue/chat";              // 서버가 고객에게 보낼 때 쓰는 이름. convertAndSendToUser(회원 ID, 이 값)로 보내면 그 회원의 위 구독으로 간다
    public static final String ADMIN_TOPIC = "/topic/admin/chat";           // 쇼핑몰 관리자 브라우저가 구독하는 채널. 모든 상담방의 새 메시지가 온다

    private final ChatSocketAuthorization authorization;  // 브라우저가 보낸 STOMP 프레임을 검사한다. CONNECT는 토큰 확인, SUBSCRIBE는 구독 권한 확인, SEND는 거부
    private TaskScheduler heartbeatScheduler;              // heartbeat 10초를 세는 타이머. Spring이 만든 스케줄러를 아래 setHeartbeatScheduler로 받는다

    public ChatWebSocketConfiguration(ChatSocketAuthorization authorization) {
        this.authorization = authorization;
    }

    // heartbeat에 쓸 스케줄러를 받는다. 이 스케줄러는 Spring이 이 설정을 읽으며 만드는 빈이라, 서로 기다리지 않도록 @Lazy로 늦게 받는다.
    @Autowired  // Spring이 이 객체를 만든 직후 이 메서드를 부르고, 매개변수에 맞는 빈(Bean)을 넣어 준다
    void setHeartbeatScheduler(@Lazy @Qualifier("messageBrokerTaskScheduler") TaskScheduler heartbeatScheduler) {
        this.heartbeatScheduler = heartbeatScheduler;
    }

    // 브라우저가 WebSocket 연결을 여는 입구(/ws)를 등록한다. Spring이 연결 입구를 만들 때 부른다.
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(ENDPOINT);
    }

    // 메시지를 채널 구독자에게 나눠 주는 브로커를 설정한다. Spring이 브로커를 만들 때 부른다.
    //  - /topic, /queue로 시작하는 채널은 서버 메모리 안의 브로커가 맡는다
    //  - 10초마다 heartbeat를 주고받아 끊긴 연결을 알아챈다
    //  - /user로 시작하는 채널은 회원별 전용 채널로 바꿔 준다
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[] {10_000, 10_000})
                .setTaskScheduler(heartbeatScheduler);
        registry.setUserDestinationPrefix("/user");
    }

    // 브라우저가 보낸 STOMP 프레임을 ChatSocketAuthorization이 먼저 검사하게 한다. Spring이 들어오는 프레임 통로를 만들 때 부른다.
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authorization);
    }
}
