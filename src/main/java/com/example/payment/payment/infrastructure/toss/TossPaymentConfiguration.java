package com.example.payment.payment.infrastructure.toss;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(TossProperties.class)
public class TossPaymentConfiguration {
    @Bean
    public RestClient tossRestClient(RestClient.Builder builder, TossProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        // 토스 결제 API의 권장 응답 대기 시간을 적용한다.
        factory.setReadTimeout(Duration.ofSeconds(60));
        return builder
                .baseUrl("https://api.tosspayments.com")
                .requestFactory(factory)
                .defaultHeaders(headers -> headers.setBasicAuth(properties.secretKey(), ""))
                .build();
    }
}
