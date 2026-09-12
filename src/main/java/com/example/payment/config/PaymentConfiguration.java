/* 파일 역할: 결제 로직에서 공통으로 사용하는 시계, 토스 설정, HTTP 클라이언트를 Spring에 등록한다. */
package com.example.payment.config;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** 결제 업무 코드가 사용할 외부 의존성을 Bean으로 구성하는 설정 클래스다. */
@Configuration
@EnableConfigurationProperties(TossProperties.class)
public class PaymentConfiguration {
    /** 생성 시각과 작업 만료 시각을 계산할 UTC 시계를 제공한다. Clock 주입으로 시간 의존성을 분리한다. */
    @Bean
    Clock paymentClock() { return Clock.systemUTC(); }

    /**
     * 토스 API 주소와 시크릿 키의 Basic 인증, 연결 3초·응답 읽기 10초 제한을 설정한다.
     * 다른 주소로 자동 리다이렉트하지 않도록 구성한 전용 RestClient를 등록한다.
     */
    @Bean
    public RestClient tossRestClient(RestClient.Builder builder, TossProperties properties) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(10));
        return builder.baseUrl("https://api.tosspayments.com")
                .requestFactory(factory)
                .defaultHeaders(headers -> headers.setBasicAuth(properties.secretKey(), ""))
                .build();
    }
}
