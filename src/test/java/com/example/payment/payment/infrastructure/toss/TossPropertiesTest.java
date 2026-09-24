/* 파일 역할: 토스 설정이 허용하는 테스트 키 형식과 키 원문 노출 방지 규칙을 검증한다. */
package com.example.payment.payment.infrastructure.toss;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 키 미설정, 지원하지 않는 키 형식, 문자열 출력에서의 키 노출을 확인하는 설정 단위 테스트다. */
class TossPropertiesTest {
    /** 키가 모두 없어도 설정 검증은 통과하되 결제 기능은 비활성화되는지 확인한다. */
    @Test
    void absentKeysDisablePaymentsButAllowOrderDevelopment() {
        TossProperties properties = new TossProperties(null, null, null, null);
        assertThat(properties.configured()).isFalse();
        assertThat(properties.isTestKeyPair()).isTrue();
    }

    /** 결제창형 테스트 키만 허용하고 구제품·운영·혼합·단일 키는 거부한다. */
    @Test
    void onlyPairedWidgetTestKeysAreAccepted() {
        assertThat(keys("test_gck_example", "test_gsk_example").isTestKeyPair()).isTrue();
        assertThat(keys("live_gck_example", "live_gsk_example").isTestKeyPair()).isFalse();
        assertThat(keys("test_gck_example", "").isTestKeyPair()).isFalse();
        assertThat(keys("", "test_gsk_example").isTestKeyPair()).isFalse();
        assertThat(keys("test_ck_example", "test_sk_example").isTestKeyPair()).isFalse();
        assertThat(keys("test_gck_example", "test_sk_example").isTestKeyPair()).isFalse();
        assertThat(keys("test_ck_example", "test_gsk_example").isTestKeyPair()).isFalse();
        assertThat(keys("test_gck_example", "live_gsk_example").isTestKeyPair()).isFalse();
        assertThat(keys("test_gck_", "test_gsk_").isTestKeyPair()).isFalse();
    }

    @Test
    void optionalVariantsAreTrimmedAndDefaultToEmpty() {
        TossProperties properties = new TossProperties(" test_gck_example ", " test_gsk_example ", " CARD_ONLY ", " TERMS ");
        assertThat(properties.isTestKeyPair()).isTrue();
        assertThat(properties.paymentMethodVariantKey()).isEqualTo("CARD_ONLY");
        assertThat(properties.agreementVariantKey()).isEqualTo("TERMS");
        assertThat(keys("", "").paymentMethodVariantKey()).isEmpty();
        assertThat(keys("", "").agreementVariantKey()).isEmpty();
    }

    /** 설정 객체의 문자열 표현에 클라이언트 키와 시크릿 키 원문이 포함되지 않는지 확인한다. */
    @Test
    void stringRepresentationDoesNotExposeKeys() {
        assertThat(keys("test_gck_example", "test_gsk_example").toString()).doesNotContain("test_gsk", "test_gck");
    }

    private TossProperties keys(String clientKey, String secretKey) {
        return new TossProperties(clientKey, secretKey, null, null);
    }
}
