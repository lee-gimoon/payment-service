package com.example.payment.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** 토스 설정과 HTTP 클라이언트를 Spring 빈으로 등록한다. */
@Configuration
@EnableConfigurationProperties(TossProperties.class)
public class PaymentConfiguration {
    /** 토스 주소, 시크릿 키 인증, 연결 3초·응답 10초 제한을 한 번 설정한다. */
    @Bean
    public RestClient tossRestClient(RestClient.Builder builder, TossProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(10));
        return builder.baseUrl("https://api.tosspayments.com")
                .requestFactory(factory)
                .defaultHeaders(headers -> headers.setBasicAuth(properties.secretKey(), ""))
                .build();
    }
}
