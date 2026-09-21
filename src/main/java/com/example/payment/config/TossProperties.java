package com.example.payment.config;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** application.yml의 payment.toss 아래 값을 받는다. clientKey는 브라우저용, secretKey는 서버 승인용이다. */
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

    /** 키 없이 주문부터 학습할 수 있다. 결제창형 테스트 키 한 쌍만 허용한다. */
    @AssertTrue(message = "주문서형·결제창형 테스트 키(test_gck_, test_gsk_) 한 쌍을 설정해주세요.")
    public boolean isTestKeyPair() {
        return (clientKey.isEmpty() && secretKey.isEmpty())
                || (clientKey.startsWith("test_gck_") && clientKey.length() > 9
                    && secretKey.startsWith("test_gsk_") && secretKey.length() > 9);
    }

    public boolean configured() {
        return !clientKey.isEmpty() && !secretKey.isEmpty();
    }

    /** 설정 객체를 출력해도 시크릿 키가 로그에 남지 않게 한다. */
    @Override
    public String toString() {
        return "TossProperties[configured=" + configured() + "]";
    }
}
