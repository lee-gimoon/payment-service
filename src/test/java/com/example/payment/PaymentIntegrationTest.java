package com.example.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.payment.gateway.TossPaymentClient;
import com.example.payment.api.error.ApiException;
import com.example.payment.order.CreateOrderRequest;
import com.example.payment.order.OrderService;
import com.example.payment.payment.ConfirmPaymentRequest;
import com.example.payment.payment.Payment;
import com.example.payment.payment.PaymentAttemptService;
import com.example.payment.payment.PaymentAttemptStatus;
import com.example.payment.payment.PaymentRepository;
import com.example.payment.payment.PaymentResult;
import com.example.payment.payment.PaymentService;
import com.example.payment.payment.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

/** 실제 PostgreSQL에서 승인 요청 안의 재조회·취소·저장 순서를 검증한다. 토스 HTTP는 모의 객체로 대체한다. */
@SpringBootTest(properties = {"payment.toss.client-key=test_gck_integration", "payment.toss.secret-key=test_gsk_integration",
        "payment.toss.payment-method-variant-key=CARD_ONLY", "payment.toss.agreement-variant-key=TERMS"})
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
    @Autowired PaymentAttemptService attempts;
    @Autowired PaymentRepository paymentRepository;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean TossPaymentClient toss;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE TABLE payments, purchase_orders CASCADE");
    }

    /** 예전 단일 상품 주문을 DB에 직접 넣어 결제 승인과 이전 주문 조회를 검증한다. */
    private String legacyOrder() {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO purchase_orders (id, product_name, quantity, amount, created_at) "
                + "VALUES (?, '티셔츠', 1, 10000, now())", id);
        return id;
    }

    @Test
    void catalogOrderUsesServerPricesAndStoresSelectedOptions() throws Exception {
        mvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10))
                .andExpect(jsonPath("$[0].id").value("tee-01"))
                .andExpect(jsonPath("$[0].price").value(19000));

        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("""
                {"items":[
                  {"productId":"tee-01","size":"M","quantity":2},
                  {"productId":"tee-04","size":"L","quantity":1}
                ]}
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(65000))
                .andExpect(jsonPath("$.quantity").value(3))
                .andExpect(jsonPath("$.items[0].unitPrice").value(19000))
                .andExpect(jsonPath("$.items[1].size").value("L"));

        assertThat(jdbc.queryForObject("SELECT amount FROM purchase_orders", Long.class)).isEqualTo(65000);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM purchase_order_items", Long.class)).isEqualTo(2);
    }

    @Test
    void confirmedCatalogOrderStillReturnsItsPurchasedItems() throws Exception {
        String orderId = orders.create(new CreateOrderRequest(List.of(
                new CreateOrderRequest.Item("tee-01", "M", 1),
                new CreateOrderRequest.Item("tee-02", "L", 1)))).orderId();
        when(toss.confirm(any(), anyLong())).thenReturn(succeeded());

        mvc.perform(post("/payments/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(confirmJson(orderId, "catalog-payment-key", "47000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(47000))
                .andExpect(jsonPath("$.payment.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.items[0].productId").value("tee-01"))
                .andExpect(jsonPath("$.items[1].productId").value("tee-02"));
    }

    @Test
    void savedOrderItemsLoadAsEntitiesWithTheirOwnIds() throws Exception {
        String orderId = orders.create(new CreateOrderRequest(List.of(
                new CreateOrderRequest.Item("tee-02", "M", 1),
                new CreateOrderRequest.Item("tee-01", "S", 1)))).orderId();

        assertThat(jdbc.queryForList(
                "SELECT id FROM purchase_order_items WHERE order_id = ? ORDER BY line_number", String.class, orderId))
                .hasSize(2).allSatisfy(id -> assertThat(id).hasSize(36));
        mvc.perform(get("/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productId").value("tee-02"))
                .andExpect(jsonPath("$.items[1].productId").value("tee-01"));
    }

    @Test
    void productPriceChangeAffectsNewOrdersButNotPastOrderSnapshot() throws Exception {
        String oldOrderId = orders.create(new CreateOrderRequest(List.of(
                new CreateOrderRequest.Item("tee-01", "M", 1)))).orderId();
        try {
            jdbc.update("UPDATE products SET price = ? WHERE id = ?", 23_000, "tee-01");

            mvc.perform(get("/products/tee-01"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.price").value(23000));
            mvc.perform(get("/orders/{id}", oldOrderId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.amount").value(19000))
                    .andExpect(jsonPath("$.items[0].unitPrice").value(19000));
            mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("""
                    {"items":[{"productId":"tee-01","size":"M","quantity":1}]}
                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.amount").value(23000));
        } finally {
            jdbc.update("UPDATE products SET price = ? WHERE id = ?", 19_000, "tee-01");
        }
    }

    @Test
    void discontinuedProductIsHiddenButPastOrderCanStillBeRead() throws Exception {
        String orderId = orders.create(new CreateOrderRequest(List.of(
                new CreateOrderRequest.Item("tee-01", "M", 1)))).orderId();
        try {
            jdbc.update("UPDATE products SET active = false WHERE id = ?", "tee-01");

            mvc.perform(get("/products"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(9));
            mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("""
                    {"items":[{"productId":"tee-01","size":"M","quantity":1}]}
                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
            mvc.perform(get("/orders/{id}", orderId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].productId").value("tee-01"))
                    .andExpect(jsonPath("$.items[0].unitPrice").value(19000));
            assertThatThrownBy(() -> jdbc.update("DELETE FROM products WHERE id = ?", "tee-01"))
                    .isInstanceOf(DataAccessException.class);
        } finally {
            jdbc.update("UPDATE products SET active = true WHERE id = ?", "tee-01");
        }
    }

    @Test
    void invalidCatalogSelectionsCannotCreateAnOrder() throws Exception {
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("""
                {"items":[{"productId":"tee-01","size":"XXL","quantity":1}]}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CART"));
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("""
                {"items":[{"productId":"missing","size":"M","quantity":1}]}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("""
                {"items":[{"productId":"tee-01","size":"M","quantity":11}]}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CART"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM purchase_orders", Long.class)).isZero();
    }

    @Test
    void matchingApprovalIsSavedWithoutLookup() throws Exception {
        String id = legacyOrder();
        when(toss.confirm(any(), anyLong())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE order_id = ?", String.class, id))
                    .isEqualTo("PROCESSING");
            return succeeded();
        });
        mvc.perform(post("/payments/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(confirmJson(id, "payment-key", "10000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.status").value("SUCCEEDED"));
        mvc.perform(get("/orders/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.status").value("SUCCEEDED"));
        verify(toss, never()).lookup(any(), anyLong());
        verify(toss, never()).cancel(any());
    }

    @Test
    void wrongRequestAmountIsRejectedBeforeCallingToss() throws Exception {
        String id = legacyOrder();
        mvc.perform(post("/payments/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(confirmJson(id, "payment-key", "9000")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AMOUNT_MISMATCH"));
        assertThat(paymentRepository.count()).isZero();
        verifyNoInteractions(toss);
    }

    @Test
    void explicitDeclineDoesNotTriggerLookup() {
        String id = legacyOrder();
        when(toss.confirm(any(), anyLong())).thenReturn(PaymentResult.failed("REJECT_CARD_COMPANY"));
        assertThat(payments.confirm(request(id, "payment-key")).payment().status()).isEqualTo(PaymentStatus.FAILED);
        verify(toss, never()).lookup(any(), anyLong());
    }

    @Test
    void uncertainApprovalIsLookedUpImmediatelyAndCanBecomeSuccessful() throws Exception {
        String id = legacyOrder();
        when(toss.confirm(any(), anyLong())).thenReturn(PaymentResult.unknown("PG_COMMUNICATION_ERROR"));
        when(toss.lookup(any(), anyLong())).thenReturn(succeeded());
        mvc.perform(post("/payments/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(confirmJson(id, "payment-key", "10000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.status").value("SUCCEEDED"));
        verify(toss).lookup(any(), anyLong());
        verify(toss, never()).cancel(any());
    }

    @Test
    void mismatchedAmountIsCanceledInTheSameRequestAfterPersistingIntent() throws Exception {
        String id = legacyOrder();
        when(toss.confirm(any(), anyLong())).thenReturn(PaymentResult.unknown("PG_RESPONSE_MISMATCH"));
        when(toss.lookup(any(), anyLong())).thenReturn(cancelRequired());
        when(toss.cancel(any())).thenAnswer(invocation -> {
            Payment sent = invocation.getArgument(0);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            Payment saved = paymentRepository.findFirstByOrderIdOrderByCreatedAtDescIdDesc(id).orElseThrow();
            assertThat(saved.getStatus()).isEqualTo(PaymentStatus.CANCEL_PENDING);
            assertThat(saved.getCancelIdempotencyKey()).isEqualTo(sent.getCancelIdempotencyKey()).isNotBlank();
            assertThat(saved.getPgAmount()).isEqualByComparingTo("9000");
            return canceled();
        });
        mvc.perform(post("/payments/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(confirmJson(id, "payment-key", "10000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.status").value("CANCELED"))
                .andExpect(jsonPath("$.payment.paidAmount").value(9000))
                .andExpect(jsonPath("$.payment.canceledAt").isString());
        verify(toss).lookup(any(), anyLong());
        verify(toss).cancel(any());
    }

    @Test
    void failedIntentSaveNeverCallsCancel() {
        String id = legacyOrder();
        when(toss.confirm(any(), anyLong())).thenReturn(PaymentResult.unknown("PG_RESPONSE_MISMATCH"));
        when(toss.lookup(any(), anyLong())).thenReturn(cancelRequired());
        jdbc.execute("""
                CREATE FUNCTION reject_cancel_intent() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                  IF NEW.status = 'CANCEL_PENDING' THEN RAISE EXCEPTION 'storage outage'; END IF;
                  RETURN NEW;
                END $$
                """);
        jdbc.execute("CREATE TRIGGER reject_cancel_intent BEFORE UPDATE ON payments "
                + "FOR EACH ROW EXECUTE FUNCTION reject_cancel_intent()");
        try {
            assertThatThrownBy(() -> payments.confirm(request(id, "payment-key")))
                    .isInstanceOf(DataAccessException.class);
        } finally {
            jdbc.execute("DROP TRIGGER reject_cancel_intent ON payments");
            jdbc.execute("DROP FUNCTION reject_cancel_intent()");
        }
        assertThat(paymentRepository.findFirstByOrderIdOrderByCreatedAtDescIdDesc(id).orElseThrow().getStatus()).isEqualTo(PaymentStatus.UNKNOWN);
        verify(toss, never()).cancel(any());
    }

    @Test
    void unrelatedPaymentIsNeverCanceled() throws Exception {
        String id = legacyOrder();
        when(toss.confirm(any(), anyLong())).thenReturn(PaymentResult.unknown("PG_RESPONSE_MISMATCH"));
        when(toss.lookup(any(), anyLong())).thenReturn(PaymentResult.reviewRequired("PG_IDENTITY_MISMATCH"));
        mvc.perform(post("/payments/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(confirmJson(id, "payment-key", "10000")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.payment.status").value("REVIEW_REQUIRED"));
        verify(toss, never()).cancel(any());
    }

    @Test
    void unresolvedLookupNeedsReviewAndOrderReadsDoNotCallToss() throws Exception {
        String id = legacyOrder();
        when(toss.confirm(any(), anyLong())).thenReturn(PaymentResult.unknown("PG_COMMUNICATION_ERROR"));
        when(toss.lookup(any(), anyLong())).thenReturn(PaymentResult.unknown("PG_LOOKUP_ERROR"));
        mvc.perform(post("/payments/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(confirmJson(id, "payment-key", "10000")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.payment.status").value("REVIEW_REQUIRED"));
        mvc.perform(get("/orders/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.status").value("REVIEW_REQUIRED"));
        payments.confirm(request(id, "payment-key"));
        verify(toss).confirm(any(), anyLong());
        verify(toss).lookup(any(), anyLong());
        verify(toss, never()).cancel(any());
    }

    @Test
    void lostCancelResponseIsCheckedOnceBeforeReturning() throws Exception {
        String id = legacyOrder();
        when(toss.confirm(any(), anyLong())).thenReturn(PaymentResult.unknown("PG_RESPONSE_MISMATCH"));
        when(toss.lookup(any(), anyLong())).thenReturn(cancelRequired(), canceled());
        when(toss.cancel(any())).thenReturn(PaymentResult.unknown("PG_CANCEL_UNCONFIRMED"));
        mvc.perform(post("/payments/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(confirmJson(id, "payment-key", "10000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.status").value("CANCELED"));
        verify(toss, times(2)).lookup(any(), anyLong());
        verify(toss).cancel(any());
    }

    @Test
    void unresolvedCancelKeepsIntentAndNeedsReview() {
        String id = legacyOrder();
        when(toss.confirm(any(), anyLong())).thenReturn(PaymentResult.unknown("PG_RESPONSE_MISMATCH"));
        when(toss.lookup(any(), anyLong())).thenReturn(cancelRequired(), cancelRequired());
        when(toss.cancel(any())).thenReturn(PaymentResult.unknown("PG_CANCEL_UNCONFIRMED"));
        assertThat(payments.confirm(request(id, "payment-key")).payment().status())
                .isEqualTo(PaymentStatus.REVIEW_REQUIRED);
        Payment saved = paymentRepository.findFirstByOrderIdOrderByCreatedAtDescIdDesc(id).orElseThrow();
        assertThat(saved.getCancelIdempotencyKey()).isNotBlank();
        assertThat(saved.getPgAmount()).isEqualByComparingTo("9000");
        verify(toss).cancel(any());
    }

    @Test
    void duplicateRequestDoesNotRepeatConfirmation() throws Exception {
        String id = legacyOrder();
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
            } finally {
                release.countDown();
            }
            assertThat(first.get(10, TimeUnit.SECONDS).payment().status()).isEqualTo(PaymentStatus.SUCCEEDED);
        }
        verify(toss).confirm(any(), anyLong());
    }

    @Test
    void closingWindowIsStoredWithoutCreatingPaymentAndOrderCanBeRetried() {
        String id = legacyOrder();
        var first = attempts.start(id);
        attempts.authenticationResult(first.id(),
                new PaymentAttemptService.AuthenticationResult(PaymentAttemptStatus.AUTH_CANCELED, "WINDOW_CLOSED"));
        assertThat(orders.get(id).status().name()).isEqualTo("PENDING_PAYMENT");
        assertThat(orders.get(id).latestAttempt().status()).isEqualTo(PaymentAttemptStatus.AUTH_CANCELED);
        assertThat(paymentRepository.count()).isZero();

        var retry = attempts.start(id);
        when(toss.confirm(any(), anyLong())).thenReturn(succeeded());
        var result = payments.confirm(new ConfirmPaymentRequest(id, "retry-key", BigDecimal.valueOf(10000), retry.id()));
        assertThat(result.status().name()).isEqualTo("CONFIRMED");
        assertThat(result.payment().status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(jdbc.queryForList("SELECT status FROM payment_attempts WHERE order_id = ? ORDER BY started_at", String.class, id))
                .containsExactly("AUTH_CANCELED", "SUCCEEDED");
        assertThatThrownBy(() -> attempts.start(id)).isInstanceOf(ApiException.class);
    }

    @Test
    void failedApprovalKeepsHistoryAndAllowsNewTransactionForSameOrder() {
        String id = legacyOrder();
        var first = attempts.start(id);
        when(toss.confirm(any(), anyLong())).thenReturn(PaymentResult.failed("REJECT_CARD_COMPANY"), succeeded());
        assertThat(payments.confirm(new ConfirmPaymentRequest(id, "failed-key", BigDecimal.valueOf(10000), first.id()))
                .payment().status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(orders.get(id).status().name()).isEqualTo("PENDING_PAYMENT");

        var retry = attempts.start(id);
        assertThat(payments.confirm(new ConfirmPaymentRequest(id, "success-key", BigDecimal.valueOf(10000), retry.id()))
                .payment().status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(jdbc.queryForList("SELECT status FROM payments WHERE order_id = ? ORDER BY created_at", String.class, id))
                .containsExactly("FAILED", "SUCCEEDED");
        assertThat(paymentRepository.count()).isEqualTo(2);
        assertThatThrownBy(() -> payments.confirm(new ConfirmPaymentRequest(id, "third-key", BigDecimal.valueOf(10000))))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void migrationPreservesOldPaymentAndRemovesPollingColumns() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("upgrade_test").defaultSchema("upgrade_test").target("1").load().migrate();
        jdbc.execute("""
                INSERT INTO upgrade_test.purchase_orders VALUES
                ('old-order', '티셔츠', 1, 10000, 'KRW', now()),
                ('old-pending', '티셔츠', 1, 10000, 'KRW', now());
                INSERT INTO upgrade_test.payments
                (order_id, attempt_id, payment_key, amount, status, operation_id, processing_until,
                 created_at, checked_at, approved_at, pg_status)
                VALUES ('old-order', 'old-attempt', 'old-key', 10000, 'SUCCEEDED', 'old-operation',
                        now(), now(), now(), now(), 'DONE');
                INSERT INTO upgrade_test.payments
                (order_id, attempt_id, payment_key, amount, status, operation_id, processing_until, created_at)
                VALUES ('old-pending', 'pending-attempt', 'pending-key', 10000, 'UNKNOWN', 'pending-operation',
                        now(), now());
                """);
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("upgrade_test").defaultSchema("upgrade_test").load().migrate();
        assertThat(jdbc.queryForMap("SELECT payment_key, status, pg_status FROM upgrade_test.payments WHERE order_id = 'old-order'"))
                .containsEntry("payment_key", "old-key").containsEntry("status", "SUCCEEDED")
                .containsEntry("pg_status", "DONE");
        assertThat(jdbc.queryForObject("SELECT status FROM upgrade_test.purchase_orders WHERE id = 'old-order'", String.class))
                .isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM upgrade_test.payment_attempts", Long.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForMap("SELECT payment_key, status, error_code FROM upgrade_test.payments WHERE order_id = 'old-pending'"))
                .containsEntry("payment_key", "pending-key").containsEntry("status", "REVIEW_REQUIRED")
                .containsEntry("error_code", "PG_RECONCILIATION_REQUIRED");
        assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns "
                + "WHERE table_schema = 'upgrade_test' AND table_name = 'payments'", String.class))
                .doesNotContain("next_action_at", "recovery_attempts");
    }

    private static PaymentResult cancelRequired() {
        return new PaymentResult(PaymentStatus.CANCEL_PENDING, "DONE", "PG_AMOUNT_MISMATCH",
                Instant.parse("2026-09-11T01:00:00Z"), BigDecimal.valueOf(9000), "KRW", null);
    }

    private static PaymentResult canceled() {
        return new PaymentResult(PaymentStatus.CANCELED, "CANCELED", null,
                Instant.parse("2026-09-11T01:00:00Z"), BigDecimal.valueOf(9000), "KRW",
                Instant.parse("2026-09-11T01:01:00Z"));
    }

    private static PaymentResult succeeded() {
        return new PaymentResult(PaymentStatus.SUCCEEDED, "DONE", null,
                Instant.parse("2026-09-11T01:00:00Z"), null, null, null);
    }

    private static ConfirmPaymentRequest request(String id, String key) {
        return new ConfirmPaymentRequest(id, key, BigDecimal.valueOf(10000));
    }

    private static String confirmJson(String id, String key, String amount) {
        return "{\"orderId\":\"" + id + "\",\"paymentKey\":\"" + key + "\",\"amount\":" + amount + "}";
    }
}
