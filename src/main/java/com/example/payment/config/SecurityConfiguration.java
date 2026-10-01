package com.example.payment.config;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthenticatedAuthorizationManager;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * 상품과 결제 설정은 공개하고, 주문·결제는 Keycloak access token을 가진 회원만,
 * {@code /admin/**}는 Keycloak realm 역할 {@code shop-admin}이 있는 쇼핑몰 관리자만 호출한다.
 * 고객 상담 창구 {@code /chat/**}는 쇼핑몰 관리자가 아닌 회원만 쓴다.
 */
@Configuration
public class SecurityConfiguration {
    /** Keycloak {@code modo-club} realm의 쇼핑몰 관리자 역할. Keycloak 관리 콘솔의 슈퍼 유저와는 별개다. */
    public static final String SHOP_ADMIN = "shop-admin";

    @Bean
    SecurityFilterChain api(HttpSecurity http, JwtAuthenticationConverter keycloakRealmRoles) throws Exception {
        AuthenticationEntryPoint unauthorized = unauthorized();
        AccessDeniedHandler forbidden = forbidden();
        // 쿠키 세션 없이 요청마다 Bearer 토큰만 확인하므로 CSRF 토큰이 필요 없다.
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET, "/products", "/products/*", "/payment-config").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/error").permitAll()
                        // 브라우저 WebSocket은 연결 요청에 Authorization 헤더를 붙일 수 없다.
                        // 토큰은 연결 직후 STOMP CONNECT 프레임에서 ChatSocketAuthorization이 검사한다.
                        .requestMatchers("/ws").permitAll()
                        .requestMatchers("/admin/**").hasRole(SHOP_ADMIN)
                        // 쇼핑몰 관리자는 고객 상담 창구를 쓰지 않고 /admin/chat에서 답한다.
                        .requestMatchers("/chat/**").access(AuthorizationManagers.allOf(
                                AuthenticatedAuthorizationManager.authenticated(),
                                AuthorizationManagers.not(AuthorityAuthorizationManager.hasRole(SHOP_ADMIN))))
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakRealmRoles))
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden));
        return http.build();
    }

    // Keycloak은 realm 역할을 access token의 realm_access.roles에 담는다.
    // Spring 기본 변환기는 scope만 권한으로 읽으므로, 역할을 hasRole()로 검사할 수 있게 ROLE_ 권한으로 바꾼다.
    // HTTP API와 WebSocket 연결이 같은 변환기를 쓴다.
    @Bean
    JwtAuthenticationConverter keycloakRealmRoles() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(SecurityConfiguration::realmRoles);
        return converter;
    }

    public static boolean isShopAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> ("ROLE_" + SHOP_ADMIN).equals(authority.getAuthority()));
    }

    private static Collection<GrantedAuthority> realmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }

    // 표준 WWW-Authenticate 헤더에 더해 다른 API 오류와 같은 {code, message} 본문을 보낸다.
    private static AuthenticationEntryPoint unauthorized() {
        BearerTokenAuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();
        return (request, response, exception) -> {
            bearer.commence(request, response, exception);
            writeError(response, "UNAUTHORIZED", "로그인이 필요합니다. 로그인한 뒤 다시 시도해주세요.");
        };
    }

    // 로그인은 했지만 필요한 역할이 없을 때. 401과 같은 형식으로 응답한다.
    private static AccessDeniedHandler forbidden() {
        BearerTokenAccessDeniedHandler bearer = new BearerTokenAccessDeniedHandler();
        return (request, response, exception) -> {
            bearer.handle(request, response, exception);
            writeError(response, "FORBIDDEN", "이 요청을 처리할 권한이 없습니다.");
        };
    }

    private static void writeError(HttpServletResponse response, String code, String message) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}
