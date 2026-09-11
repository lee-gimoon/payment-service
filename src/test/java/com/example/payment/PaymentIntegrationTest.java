package com.example.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.payment.gateway.PaymentGateway;
import com.example.payment.gateway.PaymentGateway.PaymentCommand;
import com.example.payment.order.OrderResponse;
import com.example.payment.order.OrderService;
import com.example.payment.payment.ConfirmPaymentRequest;
import com.example.payment.payment.PaymentOutcome;
import com.example.payment.payment.PaymentService;
import com.example.payment.payment.PaymentStatus;
import com.example.payment.payment.PaymentTransactions;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {"payment.toss.client-key=test_ck_integration", "payment.toss.secret-key=test_sk_integration"})
@AutoConfigureMockMvc
@Testcontainers
class PaymentIntegrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired OrderService orders;
    @Autowired PaymentService payments;
    @Autowired PaymentTransactions transactions;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean PaymentGateway gateway;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE TABLE payments, purchase_orders CASCADE");
    }

    @Test
    void completeOrderAndPaymentFlow() throws Exception {
        String body = mvc.perform(post("/orders")).andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.amount").value(10000)).andExpect(jsonPath("$.quantity").value(1))
                .andExpect(jsonPath("$.payment.status").value("READY"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.orderId");
        when(gateway.confirm(any())).thenAnswer(invocation -> {
            PaymentCommand command = invocation.getArgument(0);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(jdbc.queryForObject("SELECT attempt_id FROM payments WHERE order_id = ?", String.class, id))
                    .isEqualTo(command.attemptId());
            assertThat(jdbc.queryForObject("SELECT payment_key FROM payments WHERE order_id = ?", String.class, id))
                    .isEqualTo("payment-key");
            return succeeded();
        });
        mvc.perform(post("/payments/confirm").contentType(MediaType.APPLICATION_JSON).content(confirmJson(id, "payment-key", "10000")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.payment.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.payment.pgStatus").value("DONE"))
                .andExpect(jsonPath("$.payment.paymentKey").doesNotExist());
        mvc.perform(get("/orders/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.payment.approvedAt").value("2026-09-11T01:00:00Z"));
        payments.confirm(request(id, "payment-key"));
        payments.reconcile(id);
        verify(gateway).confirm(any());
        verify(gateway, never()).lookup(any());
    }

    @Test
    void rejectsOrderTamperingAndWrongAmountBeforeCallingPg() throws Exception {
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1,\"quantity\":50}"))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM purchase_orders", Integer.class)).isZero();
        String id = orders.create().orderId();
        mvc.perform(post("/payments/confirm").contentType(MediaType.APPLICATION_JSON).content(confirmJson(id, "payment-key", "1")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("AMOUNT_MISMATCH"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments", Integer.class)).isZero();
        verifyNoInteractions(gateway);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{invalid}", "null", "{\"orderId\":\"missing-order\",\"paymentKey\":\"key\",\"amount\":10000.5}",
            "{\"orderId\":\"missing-order\",\"paymentKey\":\"key\",\"amount\":-1}",
            "{\"orderId\":\"missing-order\",\"paymentKey\":\" \",\"amount\":10000}"})
    void invalidRequestsReturn400(String body) throws Exception {
        mvc.perform(post("/payments/confirm").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(gateway);
    }

    @Test
    void nonexistentOrderReturns404() throws Exception {
        mvc.perform(get("/orders/missing-order")).andExpect(status().isNotFound());
        mvc.perform(post("/payments/confirm").contentType(MediaType.APPLICATION_JSON)
                .content(confirmJson("missing-order", "payment-key", "10000"))).andExpect(status().isNotFound());
        mvc.perform(post("/payments/missing-order/reconcile")).andExpect(status().isNotFound());
        verifyNoInteractions(gateway);
    }

    @Test
    void clearDeclineIsStoredAndNotRetried() throws Exception {
        String id = orders.create().orderId();
        when(gateway.confirm(any())).thenReturn(PaymentOutcome.failed("REJECT_CARD_COMPANY"));
        mvc.perform(post("/payments/confirm").contentType(MediaType.APPLICATION_JSON).content(confirmJson(id, "payment-key", "10000")))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.payment.status").value("FAILED"));
        assertThat(payments.confirm(request(id, "payment-key")).payment().status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(orders.get(id).payment().approvedAt()).isNull();
        verify(gateway).confirm(any());
    }

    @Test
    void unknownResultIsRecoveredByLookupWithoutReconfirmation() throws Exception {
        String id = orders.create().orderId();
        when(gateway.confirm(any())).thenReturn(PaymentOutcome.unknown("PG_COMMUNICATION_ERROR"));
        mvc.perform(post("/payments/confirm").contentType(MediaType.APPLICATION_JSON).content(confirmJson(id, "payment-key", "10000")))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.payment.status").value("UNKNOWN"))
                .andExpect(jsonPath("$.payment.canReconcile").value(true));
        String attempt = orders.get(id).payment().attemptId();
        payments.confirm(request(id, "payment-key"));
        when(gateway.lookup(any())).thenReturn(succeeded());
        mvc.perform(post("/payments/{id}/reconcile", id)).andExpect(status().isOk()).andExpect(jsonPath("$.payment.status").value("SUCCEEDED"));
        assertThat(orders.get(id).payment().attemptId()).isEqualTo(attempt);
        verify(gateway).confirm(any());
        verify(gateway).lookup(any());
    }

    @Test
    void simultaneousConfirmationCallsPgOnlyOnceAndDoesNotHoldDbLockDuringNetwork() throws Exception {
        String id = orders.create().orderId();
        CountDownLatch pgEntered = new CountDownLatch(1);
        CountDownLatch releasePg = new CountDownLatch(1);
        doAnswer(invocation -> {
            pgEntered.countDown();
            assertThat(releasePg.await(10, TimeUnit.SECONDS)).isTrue();
            return succeeded();
        }).when(gateway).confirm(any());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> payments.confirm(request(id, "payment-key")));
            try {
                assertThat(pgEntered.await(10, TimeUnit.SECONDS)).isTrue();
                var duplicates = List.of(
                        executor.submit(() -> payments.confirm(request(id, "payment-key"))),
                        executor.submit(() -> payments.confirm(request(id, "payment-key"))),
                        executor.submit(() -> payments.reconcile(id)));
                for (var duplicate : duplicates) {
                    assertThat(duplicate.get(5, TimeUnit.SECONDS).payment().status()).isEqualTo(PaymentStatus.PROCESSING);
                }
            } finally {
                releasePg.countDown();
            }
            assertThat(first.get(10, TimeUnit.SECONDS).payment().status()).isEqualTo(PaymentStatus.SUCCEEDED);
        }
        verify(gateway).confirm(any());
        verify(gateway, never()).lookup(any());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments", Integer.class)).isOne();
    }

    @Test
    void cannotSubstitutePaymentKeyOrReuseItForAnotherOrder() throws Exception {
        String first = orders.create().orderId();
        String second = orders.create().orderId();
        when(gateway.confirm(any())).thenReturn(succeeded());
        payments.confirm(request(first, "payment-key"));
        mvc.perform(post("/payments/confirm").contentType(MediaType.APPLICATION_JSON).content(confirmJson(first, "different-key", "10000")))
                .andExpect(status().isConflict());
        mvc.perform(post("/payments/confirm").contentType(MediaType.APPLICATION_JSON).content(confirmJson(second, "payment-key", "10000")))
                .andExpect(status().isConflict());
        verify(gateway).confirm(any());
        assertThat(orders.get(first).payment().status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(orders.get(second).payment().status()).isEqualTo(PaymentStatus.READY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"P0001", "23514"})
    void databaseFailureAfterPgSuccessPreservesClaimAndCanBeRecovered(String sqlState) throws Exception {
        String id = orders.create().orderId();
        when(gateway.confirm(any())).thenReturn(succeeded());
        jdbc.execute("""
                CREATE FUNCTION reject_payment_update() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'simulated storage outage' USING ERRCODE = '%s'; END $$
                """.formatted(sqlState));
        jdbc.execute("CREATE TRIGGER reject_payment_update BEFORE UPDATE ON payments FOR EACH ROW EXECUTE FUNCTION reject_payment_update()");
        try {
            mvc.perform(post("/payments/confirm").contentType(MediaType.APPLICATION_JSON).content(confirmJson(id, "payment-key", "10000")))
                    .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("STORAGE_UNAVAILABLE"));
        } finally {
            jdbc.execute("DROP TRIGGER reject_payment_update ON payments");
            jdbc.execute("DROP FUNCTION reject_payment_update()");
        }
        assertThat(orders.get(id).payment().status()).isEqualTo(PaymentStatus.PROCESSING);
        String attempt = orders.get(id).payment().attemptId();
        expireLease(id);
        assertThat(orders.get(id).payment().status()).isEqualTo(PaymentStatus.UNKNOWN);
        when(gateway.lookup(any())).thenReturn(succeeded());
        assertThat(payments.reconcile(id).payment().status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(orders.get(id).payment().attemptId()).isEqualTo(attempt);
        verify(gateway).confirm(any());
        verify(gateway).lookup(any());
    }

    @Test
    void abandonedClaimAndLateResponseDoNotOverwriteRecoveredResult() {
        String id = orders.create().orderId();
        PaymentTransactions.Claim abandoned = transactions.claimConfirmation(request(id, "payment-key"));
        expireLease(id);
        when(gateway.lookup(any())).thenReturn(succeeded());
        assertThat(payments.reconcile(id).payment().status()).isEqualTo(PaymentStatus.SUCCEEDED);
        transactions.finish(abandoned.command(), PaymentOutcome.failed("REJECT_CARD_COMPANY"));
        assertThat(orders.get(id).payment().status()).isEqualTo(PaymentStatus.SUCCEEDED);
        verify(gateway, never()).confirm(any());
    }

    @Test
    void migrationEnforcesPriceAndOrderPaymentAmountRelationship() {
        String id = orders.create().orderId();
        assertThatThrownBy(() -> jdbc.update("UPDATE purchase_orders SET amount = 1 WHERE id = ?", id)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE purchase_orders SET quantity = 2 WHERE id = ?", id)).isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success = true", Integer.class)).isPositive();
    }

    @Test
    void publicConfigDoesNotExposeSecret() throws Exception {
        mvc.perform(get("/payment-config")).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.clientKey").value("test_ck_integration"))
                .andExpect(jsonPath("$.secretKey").doesNotExist());
    }

    private void expireLease(String orderId) {
        jdbc.update("UPDATE payments SET processing_until = now() - interval '1 second' WHERE order_id = ?", orderId);
    }

    private static PaymentOutcome succeeded() {
        return new PaymentOutcome(PaymentStatus.SUCCEEDED, "DONE", null, Instant.parse("2026-09-11T01:00:00Z"));
    }

    private static ConfirmPaymentRequest request(String id, String key) {
        return new ConfirmPaymentRequest(id, key, BigDecimal.valueOf(10_000));
    }

    private static String confirmJson(String id, String key, String amount) {
        return "{\"orderId\":\"" + id + "\",\"paymentKey\":\"" + key + "\",\"amount\":" + amount + "}";
    }
}
