package com.example.payment.payment.infrastructure.toss;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("payment.toss")
public record TossProperties(String clientKey, String secretKey,
                             String paymentMethodVariantKey, String agreementVariantKey) {
    public TossProperties {
        clientKey = normalize(clientKey);
        secretKey = normalize(secretKey);
        paymentMethodVariantKey = normalize(paymentMethodVariantKey);
        agreementVariantKey = normalize(agreementVariantKey);
    }

    // 키 미설정은 주문 개발을 허용하지만, 운영 키나 서로 다른 제품의 키는 거부한다.
    @AssertTrue(message = "주문서형·결제창형 테스트 키(test_gck_, test_gsk_) 한 쌍을 설정해주세요.")
    public boolean isTestKeyPair() {
        return (clientKey.isEmpty() && secretKey.isEmpty())
                || (clientKey.startsWith("test_gck_") && clientKey.length() > 9
                    && secretKey.startsWith("test_gsk_") && secretKey.length() > 9);
    }

    public boolean configured() {
        return !clientKey.isEmpty() && !secretKey.isEmpty();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    // record의 기본 문자열 표현으로 인증 키가 로그에 노출되지 않게 한다.
    @Override
    public String toString() {
        return "TossProperties[configured=" + configured() + "]";
    }
}
