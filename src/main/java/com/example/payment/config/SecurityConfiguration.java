package com.example.payment.config;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

/** 상품과 결제 설정은 공개하고, 주문·결제는 Keycloak access token을 가진 회원만 호출한다. */
@Configuration
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        AuthenticationEntryPoint unauthorized = unauthorized();
        // 쿠키 세션 없이 요청마다 Bearer 토큰만 확인하므로 CSRF 토큰이 필요 없다.
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET, "/products", "/products/*", "/payment-config").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(unauthorized))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(unauthorized));
        return http.build();
    }

    // 표준 WWW-Authenticate 헤더에 더해 다른 API 오류와 같은 {code, message} 본문을 보낸다.
    private static AuthenticationEntryPoint unauthorized() {
        BearerTokenAuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();
        return (request, response, exception) -> {
            bearer.commence(request, response, exception);
            writeError(response, "UNAUTHORIZED", "로그인이 필요합니다. 로그인한 뒤 다시 시도해주세요.");
        };
    }

    private static void writeError(HttpServletResponse response, String code, String message) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}
