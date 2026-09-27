package com.example.payment.payment.infrastructure.toss;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TossPropertiesTest {
    @Test
    void absentKeysDisablePaymentsButAllowOrderDevelopment() {
        TossProperties properties = new TossProperties(null, null, null, null);
        assertThat(properties.configured()).isFalse();
        assertThat(properties.isTestKeyPair()).isTrue();
    }

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

    @Test
    void stringRepresentationDoesNotExposeKeys() {
        assertThat(keys("test_gck_example", "test_gsk_example").toString()).doesNotContain("test_gsk", "test_gck");
    }

    private TossProperties keys(String clientKey, String secretKey) {
        return new TossProperties(clientKey, secretKey, null, null);
    }
}
