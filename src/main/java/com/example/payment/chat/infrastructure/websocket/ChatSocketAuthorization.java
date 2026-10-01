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
 * 브라우저가 보낸 STOMP 프레임을 검사한다.
 * <ul>
 *   <li>CONNECT: Authorization 헤더의 access token을 HTTP API와 같은 방식으로 검증하고 연결에 회원을 붙인다.</li>
 *   <li>SUBSCRIBE: 고객은 자기 대화 주소만, 쇼핑몰 관리자는 관리자 주소만 구독할 수 있다.</li>
 *   <li>SEND: 받지 않는다. 브로커로 바로 보내면 다른 구독자에게 가짜 메시지를 뿌릴 수 있기 때문이다.</li>
 * </ul>
 * 거부하면 Spring이 ERROR 프레임을 보내고 연결을 닫는다.
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
