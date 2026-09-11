package com.example.payment.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TossPropertiesTest {
    @Test
    void absentKeysDisablePaymentsButAllowOrderDevelopment() {
        TossProperties properties = new TossProperties(null, null);
        assertThat(properties.configured()).isFalse();
        assertThat(properties.isTestKeyPair()).isTrue();
    }

    @Test
    void onlyPairedIndividualIntegrationTestKeysAreAccepted() {
        assertThat(new TossProperties("test_ck_example", "test_sk_example").isTestKeyPair()).isTrue();
        assertThat(new TossProperties("live_ck_example", "live_sk_example").isTestKeyPair()).isFalse();
        assertThat(new TossProperties("test_ck_example", "").isTestKeyPair()).isFalse();
        assertThat(new TossProperties("", "test_sk_example").isTestKeyPair()).isFalse();
        assertThat(new TossProperties("test_gck_example", "test_gsk_example").isTestKeyPair()).isFalse();
    }

    @Test
    void stringRepresentationDoesNotExposeKeys() {
        assertThat(new TossProperties("test_ck_example", "test_sk_example").toString()).doesNotContain("test_sk", "test_ck");
    }
}
