package com.example.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.payment.gateway.TossPaymentClient;
import com.example.payment.order.OrderService;
import com.example.payment.payment.ConfirmPaymentRequest;
import com.example.payment.payment.Payment;
import com.example.payment.payment.PaymentRepository;
import com.example.payment.payment.PaymentResult;
import com.example.payment.payment.PaymentService;
import com.example.payment.payment.PaymentStatus;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
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

/** 실제 PostgreSQL에서 API와 저장을 검증한다. 토스 HTTP 호출만 대체하므로 실제 결제는 발생하지 않는다. */
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
    @Autowired PaymentRepository paymentRepository;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean TossPaymentClient toss;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE TABLE payments, purchase_orders CASCADE");
    }

    @Test
    void orderAuthenticationConfirmationAndLookupKeepTheReactContract() throws Exception {
        String body = mvc.perform(post("/orders"))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.amount").value(10000)).andExpect(jsonPath("$.quantity").value(1))
                .andExpect(jsonPath("$.currency").value("KRW"))
                .andExpect(jsonPath("$.payment.status").value("READY"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.orderId");

        when(toss.confirm(any(), anyLong())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat((long) invocation.getArgument(1)).isEqualTo(10000);
            assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE order_id = ?", String.class, id))
                    .isEqualTo("PROCESSING");
            assertThat(jdbc.queryForObject("SELECT payment_key FROM payments WHERE order_id = ?", String.class, id))
                    .isEqualTo("payment-key");
            return succeeded();
        });
        mvc.perform(post("/payments/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(confirmJson(id, "payment-key", "10000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.payment.pgStatus").value("DONE"))
                .andExpect(jsonPath("$.payment.canReconcile").value(false))
                .andExpect(jsonPath("$.payment.attemptId").doesNotExist())
                .andExpect(jsonPath("$.payment.paymentKey").doesNotExist());
        mvc.perform(get("/orders/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.payment.approvedAt").value("2026-09-11T01:00:00Z"))
                .andExpect(jsonPath("$.payment.checkedAt").isString());
        payments.confirm(request(id, "payment-key"));
        payments.reconcile(id);
        verify(toss).confirm(any(), anyLong());
        verify(toss, never()).lookup(any(), anyLong());
    }

    @Test
    void ignoresClientProductValuesAndRejectsWrongApprovalAmount() throws Exception {
        String body = mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1,\"quantity\":50}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(10000)).andExpect(jsonPath("$.quantity").value(1))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.orderId");
        mvc.perform(post("/payments/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(confirmJson(id, "payment-key", "1")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("AMOUNT_MISMATCH"));
        assertThat(paymentRepository.count()).isZero();
        verifyNoInteractions(toss);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{invalid}", "null",
            "{\"orderId\":\"missing-order\",\"paymentKey\":\"key\",\"amount\":10000.5}",
            "{\"orderId\":\"missing-order\",\"paymentKey\":\"key\",\"amount\":-1}",
            "{\"orderId\":\"missing-order\",\"paymentKey\":\" \",\"amount\":10000}"})
    void invalidRequestsReturn400(String body) throws Exception {
        mvc.perform(post("/payments/confirm").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(toss);
    }

    @Test
    void nonexistentOrderReturns404() throws Exception {
        mvc.perform(get("/orders/missing-order")).andExpect(status().isNotFound());
        mvc.perform(post("/payments/confirm").contentType(MediaType.APPLICATION_JSON)
                .content(confirmJson("missing-order", "key", "10000"))).andExpect(status().isNotFound());
        mvc.perform(post("/payments/missing-order/reconcile")).andExpect(status().isNotFound());
        verifyNoInteractions(toss);
    }

    @Test
    void clearDeclineIsStoredAndNotRetried() throws Exception {
        String id = orders.create().orderId();
        when(toss.confirm(any(), anyLong())).thenReturn(PaymentResult.failed("REJECT_CARD_COMPANY"));
        mvc.perform(post("/payments/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(confirmJson(id, "payment-key", "10000")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.payment.status").value("FAILED"));
        assertThat(payments.confirm(request(id, "payment-key")).payment().status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(orders.get(id).payment().approvedAt()).isNull();
        verify(toss).confirm(any(), anyLong());
    }

    @Test
    void unknownResultIsRecoveredByLookupWithoutReconfirmation() throws Exception {
        String id = orders.create().orderId();
        when(toss.confirm(any(), anyLong())).thenReturn(PaymentResult.unknown("PG_COMMUNICATION_ERROR"));
        mvc.perform(post("/payments/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(confirmJson(id, "payment-key", "10000")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.payment.status").value("UNKNOWN"))
                .andExpect(jsonPath("$.payment.canReconcile").value(true));
        payments.confirm(request(id, "payment-key"));
        when(toss.lookup(any(), anyLong())).thenReturn(succeeded());
        mvc.perform(post("/payments/{id}/reconcile", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.payment.status").value("SUCCEEDED"));
        verify(toss).confirm(any(), anyLong());
        verify(toss).lookup(any(), anyLong());
    }

    @Test
    void simultaneousDuplicateDoesNotRepeatApprovalOrWaitForNetwork() throws Exception {
        String id = orders.create().orderId();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(toss.confirm(any(), anyLong())).thenAnswer(invocation -> {
            entered.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return succeeded();
        });
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> payments.confirm(request(id, "payment-key")));
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                var duplicate = executor.submit(() -> payments.confirm(request(id, "payment-key")));
                assertThat(duplicate.get(3, TimeUnit.SECONDS).payment().status()).isEqualTo(PaymentStatus.PROCESSING);
                assertThat(orders.get(id).payment().canReconcile()).isTrue();
            } finally {
                release.countDown();
            }
            assertThat(first.get(10, TimeUnit.SECONDS).payment().status()).isEqualTo(PaymentStatus.SUCCEEDED);
        }
        verify(toss).confirm(any(), anyLong());
        assertThat(paymentRepository.count()).isOne();
    }

    @Test
    void paymentKeyCannotBeReplacedOrUsedForAnotherOrder() throws Exception {
        String first = orders.create().orderId();
        String second = orders.create().orderId();
        when(toss.confirm(any(), anyLong())).thenReturn(succeeded());
        payments.confirm(request(first, "payment-key"));
        mvc.perform(post("/payments/confirm").contentType(MediaType.APPLICATION_JSON)
                .content(confirmJson(first, "different-key", "10000"))).andExpect(status().isConflict());
        mvc.perform(post("/payments/confirm").contentType(MediaType.APPLICATION_JSON)
                .content(confirmJson(second, "payment-key", "10000"))).andExpect(status().isConflict());
        verify(toss).confirm(any(), anyLong());
        assertThat(orders.get(second).payment().status()).isEqualTo(PaymentStatus.READY);
    }

    @ParameterizedTest
    @CsvSource({"P0001,503", "23514,409"})
    void failedResultSaveLeavesPaymentKeyAvailableForRecovery(String sqlState, int httpStatus) throws Exception {
        String id = orders.create().orderId();
        when(toss.confirm(any(), anyLong())).thenReturn(succeeded());
        jdbc.execute("""
                CREATE FUNCTION reject_payment_update() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'simulated storage outage' USING ERRCODE = '%s'; END $$
                """.formatted(sqlState));
        jdbc.execute("CREATE TRIGGER reject_payment_update BEFORE UPDATE ON payments FOR EACH ROW EXECUTE FUNCTION reject_payment_update()");
        try {
            mvc.perform(post("/payments/confirm").contentType(MediaType.APPLICATION_JSON)
                            .content(confirmJson(id, "payment-key", "10000")))
                    .andExpect(status().is(httpStatus));
        } finally {
            jdbc.execute("DROP TRIGGER reject_payment_update ON payments");
            jdbc.execute("DROP FUNCTION reject_payment_update()");
        }
        assertThat(orders.get(id).payment().status()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(orders.get(id).payment().canReconcile()).isTrue();
        when(toss.lookup(any(), anyLong())).thenReturn(succeeded());
        assertThat(payments.reconcile(id).payment().status()).isEqualTo(PaymentStatus.SUCCEEDED);
        verify(toss).confirm(any(), anyLong());
        verify(toss).lookup(any(), anyLong());
    }

    @Test
    void lateConfirmationCannotOverwriteARecoveredSuccess() throws Exception {
        String id = orders.create().orderId();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(toss.confirm(any(), anyLong())).thenAnswer(invocation -> {
            entered.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return PaymentResult.unknown("PG_COMMUNICATION_ERROR");
        });
        when(toss.lookup(any(), anyLong())).thenReturn(succeeded());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> payments.confirm(request(id, "payment-key")));
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(payments.reconcile(id).payment().status()).isEqualTo(PaymentStatus.SUCCEEDED);
            } finally {
                release.countDown();
            }
            assertThatThrownBy(() -> first.get(10, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(OptimisticLockingFailureException.class);
        }
        assertThat(orders.get(id).payment().status()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void abandonedProcessingCanBeLookedUpWithoutAnExpiryTimer() {
        String id = orders.create().orderId();
        paymentRepository.saveAndFlush(new Payment(id, "payment-key"));
        when(toss.lookup(any(), anyLong())).thenReturn(succeeded());
        assertThat(payments.reconcile(id).payment().status()).isEqualTo(PaymentStatus.SUCCEEDED);
        verify(toss, never()).confirm(any(), anyLong());
    }

    @Test
    void schemaHasOnlyTheColumnsUsedByTheLearningVersion() {
        assertThat(columns("public", "purchase_orders"))
                .containsExactlyInAnyOrder("id", "product_name", "quantity", "amount", "created_at");
        assertThat(columns("public", "payments"))
                .containsExactlyInAnyOrder("order_id", "payment_key", "status", "approved_at",
                        "checked_at", "pg_status", "error_code", "version");
        String id = orders.create().orderId();
        assertThatThrownBy(() -> jdbc.update("UPDATE purchase_orders SET amount = 1 WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void migrationUpgradesOldRowsWithoutDeletingOrdersOrPaymentResults() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("upgrade_test").defaultSchema("upgrade_test").target("1").load().migrate();
        jdbc.execute("""
                INSERT INTO upgrade_test.purchase_orders VALUES
                ('old-order', '티셔츠', 1, 10000, 'KRW', now());
                INSERT INTO upgrade_test.payments
                (order_id, attempt_id, payment_key, amount, status, operation_id, processing_until,
                 created_at, checked_at, approved_at, pg_status)
                VALUES ('old-order', 'old-attempt', 'old-key', 10000, 'SUCCEEDED', 'old-operation',
                        now(), now(), now(), now(), 'DONE');
                """);
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("upgrade_test").defaultSchema("upgrade_test").load().migrate();
        assertThat(jdbc.queryForObject("SELECT amount FROM upgrade_test.purchase_orders WHERE id = 'old-order'", Long.class))
                .isEqualTo(10000L);
        assertThat(jdbc.queryForMap("SELECT payment_key, status, pg_status, version FROM upgrade_test.payments"))
                .containsEntry("payment_key", "old-key").containsEntry("status", "SUCCEEDED")
                .containsEntry("pg_status", "DONE").containsEntry("version", 0L);
        assertThat(columns("upgrade_test", "payments")).hasSize(8);
    }

    @Test
    void publicConfigDoesNotExposeSecret() throws Exception {
        mvc.perform(get("/payment-config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.clientKey").value("test_ck_integration"))
                .andExpect(jsonPath("$.secretKey").doesNotExist());
    }

    private java.util.List<String> columns(String schema, String table) {
        return jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema = ? AND table_name = ?",
                String.class, schema, table);
    }

    private static PaymentResult succeeded() {
        return new PaymentResult(PaymentStatus.SUCCEEDED, "DONE", null, Instant.parse("2026-09-11T01:00:00Z"));
    }

    private static ConfirmPaymentRequest request(String id, String key) {
        return new ConfirmPaymentRequest(id, key, BigDecimal.valueOf(10000));
    }

    private static String confirmJson(String id, String key, String amount) {
        return "{\"orderId\":\"" + id + "\",\"paymentKey\":\"" + key + "\",\"amount\":" + amount + "}";
    }
}
