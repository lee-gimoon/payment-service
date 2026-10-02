package com.example.payment.chat.infrastructure.websocket;

import com.example.payment.config.SecurityConfiguration;
import java.security.Principal;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;

/**
 * 브라우저가 보낸 STOMP 프레임을 브로커에 닿기 전에 검사한다.
 * HTTP 요청을 컨트롤러에 닿기 전에 검사하는 Spring Security 필터와 같은 자리다.
 * 우리 코드가 부르지 않는다. ChatWebSocketConfiguration에 등록해 두면 Spring이 프레임이 들어올 때마다 preSend를 부른다.
 * <ul>
 *   <li>CONNECT: Authorization 헤더의 access token을 HTTP API와 같은 방식으로 검증하고, 이 연결이 어느 회원 것인지 기억시킨다.
 *       /user 채널을 회원별 전용 채널로 바꿀 때 이 정보를 쓴다.</li>
 *   <li>SUBSCRIBE: 고객은 고객 채널(/user/queue/chat)만, 쇼핑몰 관리자는 관리자 채널(/topic/admin/chat)만 구독할 수 있다.</li>
 *   <li>SEND: 거부한다. 브라우저가 채널로 바로 보내면 저장도 검사도 거치지 않은 가짜 메시지가 구독자에게 그대로 간다.
 *       메시지는 HTTP API로만 보낸다.</li>
 * </ul>
 * 그 밖의 프레임은 그대로 통과시킨다. 거부하면 Spring이 ERROR 프레임을 보내고 연결을 닫는다.
 */
@Component
public class ChatSocketAuthorization implements ChannelInterceptor {
    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter keycloakRealmRoles;

    public ChatSocketAuthorization(JwtDecoder jwtDecoder, JwtAuthenticationConverter keycloakRealmRoles) {
        this.jwtDecoder = jwtDecoder;
        this.keycloakRealmRoles = keycloakRealmRoles;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        switch (accessor.getCommand()) {
            case CONNECT -> accessor.setUser(authenticate(accessor.getFirstNativeHeader("Authorization")));
            case SUBSCRIBE -> authorizeSubscription(accessor.getUser(), accessor.getDestination());
            case SEND -> throw new AccessDeniedException("메시지는 HTTP API로 보내주세요.");
            default -> { }
        }
        return message;
    }

    private Authentication authenticate(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new BadCredentialsException("로그인이 필요합니다.");
        }
        try {
            return keycloakRealmRoles.convert(jwtDecoder.decode(authorization.substring("Bearer ".length())));
        } catch (JwtException exception) {
            throw new BadCredentialsException("로그인이 필요합니다.");
        }
    }

    private static void authorizeSubscription(Principal user, String destination) {
        if (!(user instanceof Authentication authentication)) {
            throw new AccessDeniedException("로그인이 필요합니다.");
        }
        // HTTP API와 같이 쇼핑몰 관리자는 고객 주소를, 고객은 관리자 주소를 구독할 수 없다.
        boolean shopAdmin = SecurityConfiguration.isShopAdmin(authentication);
        boolean allowed = shopAdmin
                ? ChatWebSocketConfiguration.ADMIN_TOPIC.equals(destination)
                : ChatWebSocketConfiguration.CUSTOMER_SUBSCRIPTION.equals(destination);
        if (!allowed) {
            throw new AccessDeniedException("구독할 수 없는 주소입니다.");
        }
    }
}
