package com.example.payment.payment.infrastructure.toss;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * TossPaymentClient가 사용할 RestClient의 생성·설정 방법을 정의하는 Spring 설정 클래스다.
 * 토스 주소·Basic 인증·타임아웃 같은 공통 통신 설정을 한 번 적용해 빈으로 등록함으로써,
 * HTTP 클라이언트의 생성·설정 책임을 실제 승인·조회 요청 로직에서 분리한다.
 * {@code @Configuration}은 이 클래스가 {@code @Bean}을 정의하는 Spring 설정 클래스임을 나타낸다.
 * {@code @EnableConfigurationProperties(TossProperties.class)}는 Spring Boot가
 * application.yml의 payment.toss 값을 생성자 인자로 전달하여 TossProperties 객체를 만들고,
 * 다른 빈에 주입할 수 있도록 그 TossProperties 객체를 Spring 빈으로 등록하게 한다.
 */
@Configuration
@EnableConfigurationProperties(TossProperties.class)
public class PaymentConfiguration {
    /**
     * RestClient는 저수준 HTTP 클라이언트의 차이를 감추고 JSON 변환·오류 처리·헤더 설정을
     * 일관된 방식으로 제공하는 Spring Framework의 동기식 HTTP 요청 API다.
     * 이 메서드는 토스 주소·시크릿 키 Basic 인증, 연결 3초·응답 60초 제한을 한 번 적용한 뒤
     * 완성된 RestClient를 {@code tossRestClient}라는 Spring 빈으로 등록한다.
     * Spring은 이 {@code @Bean} 메서드를 호출할 때 매개변수 타입에 맞는 빈을 자동으로 찾아 주입한다.
     */
    @Bean
    public RestClient tossRestClient(RestClient.Builder builder, TossProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3)) // 토스 서버와 연결을 맺는 데 최대 3초까지만 기다린다.
                .followRedirects(HttpClient.Redirect.NEVER) // 서버가 다른 주소를 알려줘도 그 주소로 자동 재요청하지 않는다.
                .build();
        // RestClient와 JDK HttpClient는 HTTP 요청을 표현하고 실행하는 규격이 달라 중간 연결이 필요하다.
        // 이 Factory는 RestClient의 요청을 JDK HttpClient가 처리할 형태로 바꾸어 전달하는 어댑터다.
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        // 토스 서버와 연결된 뒤 응답 데이터가 도착할 때까지 최대 60초 기다리도록 설정하는 메서드다. 토스 공식 타임아웃 가이드의 결제 API 권장값이다.
        factory.setReadTimeout(Duration.ofSeconds(60));
        return builder
                .baseUrl("https://api.tosspayments.com") // 이후 .uri("/v1/payments/confirm")와 합쳐 최종 요청 주소를 만든다.
                .requestFactory(factory) // 앞에서 만든 어댑터와 JDK HttpClient를 통해 요청을 전송하도록 설정한다.
                .defaultHeaders(headers -> headers.setBasicAuth(properties.secretKey(), "")) // 모든 요청에 Basic Base64(secretKey + ":") 인증 헤더를 추가한다.
                .build();
    }
}
