package com.example.payment.config;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(TossProperties.class)
public class PaymentConfiguration {
    @Bean
    Clock paymentClock() { return Clock.systemUTC(); }

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
