/* 파일 역할: 토스 설정이 허용하는 테스트 키 형식과 키 원문 노출 방지 규칙을 검증한다. */
package com.example.payment.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 키 미설정, 지원하지 않는 키 형식, 문자열 출력에서의 키 노출을 확인하는 설정 단위 테스트다. */
class TossPropertiesTest {
    /** 키가 모두 없어도 설정 검증은 통과하되 결제 기능은 비활성화되는지 확인한다. */
    @Test
    void absentKeysDisablePaymentsButAllowOrderDevelopment() {
        TossProperties properties = new TossProperties(null, null);
        assertThat(properties.configured()).isFalse();
        assertThat(properties.isTestKeyPair()).isTrue();
    }

    /** 개별 연동 테스트 키 접두사 쌍만 허용하고 운영 키·단일 키·위젯 키는 거부하는지 확인한다. */
    @Test
    void onlyPairedIndividualIntegrationTestKeysAreAccepted() {
        assertThat(new TossProperties("test_ck_example", "test_sk_example").isTestKeyPair()).isTrue();
        assertThat(new TossProperties("live_ck_example", "live_sk_example").isTestKeyPair()).isFalse();
        assertThat(new TossProperties("test_ck_example", "").isTestKeyPair()).isFalse();
        assertThat(new TossProperties("", "test_sk_example").isTestKeyPair()).isFalse();
        assertThat(new TossProperties("test_gck_example", "test_gsk_example").isTestKeyPair()).isFalse();
    }

    /** 설정 객체의 문자열 표현에 클라이언트 키와 시크릿 키 원문이 포함되지 않는지 확인한다. */
    @Test
    void stringRepresentationDoesNotExposeKeys() {
        assertThat(new TossProperties("test_ck_example", "test_sk_example").toString()).doesNotContain("test_sk", "test_ck");
    }
}
