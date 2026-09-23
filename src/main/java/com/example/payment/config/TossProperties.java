package com.example.payment.config;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 토스 결제에 필요한 Java 코드 밖에서 공급되는 환경별 설정값(외부 설정값)을
 * 하나의 불변 객체로 묶어 보관한다.
 * clientKey는 브라우저에서 결제창을 열 때 사용하고, secretKey는 서버가 토스에 승인 요청을 보낼 때 사용한다.
 * {@code @ConfigurationProperties("payment.toss")}는 Spring Boot가 application.yml의
 * payment.toss 아래 값을 이름이 대응하는 record 필드에 바인딩하도록 지정한다.
 * 예를 들어 client-key는 clientKey에, secret-key는 secretKey에 연결된다.
 * {@code @Validated}는 바인딩이 끝난 설정값에 {@code @AssertTrue} 등의 검증을 적용한다.
 */
@Validated
@ConfigurationProperties("payment.toss")
public record TossProperties(String clientKey, String secretKey,
                             String paymentMethodVariantKey, String agreementVariantKey) {
    public TossProperties {
        clientKey = clientKey == null ? "" : clientKey.trim();
        secretKey = secretKey == null ? "" : secretKey.trim();
        paymentMethodVariantKey = paymentMethodVariantKey == null ? "" : paymentMethodVariantKey.trim();
        agreementVariantKey = agreementVariantKey == null ? "" : agreementVariantKey.trim();
    }

    /**
     * 키 없이 주문부터 학습할 수 있게 하되, 키를 설정한다면 결제창형 테스트 키 한 쌍만 허용한다.
     * {@code @AssertTrue}는 설정 바인딩 후 이 메서드의 결과가 true인지 검증하여,
     * 한쪽 키가 없거나 형식이 잘못된 설정으로 서버가 실행되는 것을 막는다.
     */
    @AssertTrue(message = "주문서형·결제창형 테스트 키(test_gck_, test_gsk_) 한 쌍을 설정해주세요.")
    public boolean isTestKeyPair() {
        return (clientKey.isEmpty() && secretKey.isEmpty())
                || (clientKey.startsWith("test_gck_") && clientKey.length() > 9
                    && secretKey.startsWith("test_gsk_") && secretKey.length() > 9);
    }

    /**
     * 클라이언트 키와 시크릿 키가 모두 있어 설정상 토스 결제 기능을 사용할 수 있는지 반환한다.
     * Controller와 Service에서 두 키의 빈 문자열 검사를 반복하지 않고 같은 조건을 재사용하려고 만들었다.
     */
    public boolean configured() {
        return !clientKey.isEmpty() && !secretKey.isEmpty();
    }

    /** 설정 객체를 출력해도 시크릿 키가 로그에 남지 않게 한다. */
    @Override
    public String toString() {
        return "TossProperties[configured=" + configured() + "]";
    }
}
