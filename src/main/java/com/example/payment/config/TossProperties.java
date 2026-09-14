package com.example.payment.config;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** application.yml의 payment.toss 아래 값을 받는다. clientKey는 브라우저용, secretKey는 서버 승인용이다. */
@Validated
@ConfigurationProperties("payment.toss")
public record TossProperties(String clientKey, String secretKey) {
    public TossProperties {
        clientKey = clientKey == null ? "" : clientKey.trim();
        secretKey = secretKey == null ? "" : secretKey.trim();
    }

    /** 키 없이 주문부터 학습할 수 있다. 결제를 사용할 때는 개별 연동 테스트 키 한 쌍만 허용한다. */
    @AssertTrue(message = "API 개별 연동 테스트 키(test_ck_, test_sk_) 한 쌍을 설정해주세요.")
    public boolean isTestKeyPair() {
        return (clientKey.isEmpty() && secretKey.isEmpty())
                || (clientKey.startsWith("test_ck_") && secretKey.startsWith("test_sk_"));
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
