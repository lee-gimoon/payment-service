package com.example.payment.config;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("payment.toss")
public record TossProperties(String clientKey, String secretKey) {
    public TossProperties {
        clientKey = clientKey == null ? "" : clientKey.trim();
        secretKey = secretKey == null ? "" : secretKey.trim();
    }

    @AssertTrue(message = "MVP는 API 개별 연동 테스트 키(test_ck_, test_sk_) 한 쌍만 지원합니다.")
    public boolean isTestKeyPair() {
        return (clientKey.isEmpty() && secretKey.isEmpty())
                || (clientKey.startsWith("test_ck_") && secretKey.startsWith("test_sk_"));
    }

    public boolean configured() { return !clientKey.isEmpty() && !secretKey.isEmpty(); }

    @Override
    public String toString() { return "TossProperties[configured=" + configured() + "]"; }
}
