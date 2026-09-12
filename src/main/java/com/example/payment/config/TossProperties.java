/* 파일 역할: application.yml의 payment.toss 설정을 읽어 사용할 키 형식을 검증한다. */
package com.example.payment.config;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 브라우저용 클라이언트 키와 서버용 시크릿 키를 보관하는 불변 설정 객체다. */
@Validated
@ConfigurationProperties("payment.toss")
public record TossProperties(String clientKey, String secretKey) {
    /** record의 생성 시점에 누락 값을 빈 문자열로 바꾸고 키의 앞뒤 공백을 제거한다. */
    public TossProperties {
        clientKey = clientKey == null ? "" : clientKey.trim();
        secretKey = secretKey == null ? "" : secretKey.trim();
    }

    /**
     * 키가 모두 없거나 개별 연동 테스트 키의 접두사를 가진 두 값인지 검사한다.
     * 같은 상점의 실제 유효한 키인지는 이 문자열 검사만으로 확인할 수 없다.
     */
    @AssertTrue(message = "MVP는 API 개별 연동 테스트 키(test_ck_, test_sk_) 한 쌍만 지원합니다.")
    public boolean isTestKeyPair() {
        return (clientKey.isEmpty() && secretKey.isEmpty())
                || (clientKey.startsWith("test_ck_") && secretKey.startsWith("test_sk_"));
    }

    /** 두 키가 모두 채워져 있어 결제 기능을 사용할 설정이 있는지 반환한다. */
    public boolean configured() { return !clientKey.isEmpty() && !secretKey.isEmpty(); }

    /** 객체가 로그 등에 문자열로 출력되어도 키 원문이 노출되지 않게 설정 여부만 반환한다. */
    @Override
    public String toString() { return "TossProperties[configured=" + configured() + "]"; }
}
