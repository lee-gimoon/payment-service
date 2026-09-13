/* 파일 역할: Spring Boot가 payment.toss 설정을 연결할 객체를 정의하고, 사용할 키 형식을 검증한다. */
/*
 * 설정값이 TossProperties에 들어오는 순서:
 * 1. Spring Boot가 application.yml을 읽고 payment.toss.client-key와 payment.toss.secret-key를 설정값으로 보관한다.
 * 2. PaymentConfiguration의 @EnableConfigurationProperties(TossProperties.class)가 이 설정 객체를 Spring에 등록한다.
 * 3. 아래 @ConfigurationProperties("payment.toss")가 연결할 설정 경로를 지정한다.
 *    Spring이 client-key와 secret-key를 각각 record의 clientKey와 secretKey에 넣어 객체를 만든다.
 */
package com.example.payment.config;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 브라우저용 클라이언트 키와 서버용 시크릿 키를 보관하는 불변 설정 객체다. */
// Validated(검증): 설정값을 채운 뒤 이 객체의 검증 규칙(@AssertTrue)을 실행한다.
@Validated
// ConfigurationProperties(설정 속성): Spring이 payment.toss 아래 값을 TossProperties record의 생성자 매개변수 clientKey와 secretKey에 넣도록 지정한다.
@ConfigurationProperties("payment.toss")
public record TossProperties(String clientKey, String secretKey) {
    /** record의 생성 시점에 누락 값을 빈 문자열로 바꾸고 키의 앞뒤 공백을 제거한다. */
    public TossProperties {
        clientKey = clientKey == null ? "" : clientKey.trim();
        secretKey = secretKey == null ? "" : secretKey.trim();
    }

    /**
     * 앱 시작 시 잘못된 키 설정을 걸러낸다. @AssertTrue는 이 메서드가 true를 반환해야 검증을 통과시킨다.
     * 두 키가 모두 비어 있으면 true: 앱은 시작하고 configured()가 false라 결제는 비활성화된다.
     * 두 키가 각각 test_ck_, test_sk_로 시작하면 true: 앱은 시작하고 configured()가 true가 된다.
     * 한 키만 있거나 접두사가 틀리면 false: 설정 검증 오류로 앱 시작이 실패한다.
     * 접두사만 검사하므로 키가 실제로 유효한지, 같은 상점의 한 쌍인지는 확인하지 못한다.
     */
    @AssertTrue(message = "MVP는 API 개별 연동 테스트 키(test_ck_, test_sk_) 한 쌍만 지원합니다.")
    public boolean isTestKeyPair() {
        return (clientKey.isEmpty() && secretKey.isEmpty())
                || (clientKey.startsWith("test_ck_") && secretKey.startsWith("test_sk_"));
    }

    /** configured(설정됨): 두 키가 모두 비어 있지 않으면 true, 하나라도 비어 있으면 false를 반환한다. */
    public boolean configured() { return !clientKey.isEmpty() && !secretKey.isEmpty(); }

    /** 객체가 로그 등에 문자열로 출력되어도 키 원문이 노출되지 않게 설정 여부만 반환한다. */
    @Override
    public String toString() { return "TossProperties[configured=" + configured() + "]"; }
}
