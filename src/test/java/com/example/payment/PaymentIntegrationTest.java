package com.example.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.payment.api.error.ApiException;
import com.example.payment.order.CreateOrderRequest;
import com.example.payment.order.OrderService;
import com.example.payment.payment.api.ConfirmPaymentRequest;
import com.example.payment.payment.application.PaymentAttemptService;
import com.example.payment.payment.application.PaymentService;
import com.example.payment.payment.domain.Payment;
import com.example.payment.payment.domain.PaymentAttemptStatus;
import com.example.payment.payment.domain.PaymentResult;
import com.example.payment.payment.domain.PaymentStatus;
import com.example.payment.payment.infrastructure.toss.TossPaymentClient;
import com.example.payment.payment.persistence.PaymentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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

    @Test
    void freshDatabaseUsesOneInitialMigration() {
        assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL",
                String.class)).containsExactly("1");
        assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns "
                + "WHERE table_schema = 'public' AND table_name = 'payments'", String.class))
                .contains("requested_amount", "requested_currency", "pg_amount", "pg_currency")
                .doesNotContain("canceled_at", "cancel_idempotency_key", "cancel_requested_at", "next_action_at");
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
    void confirmedOrderReturnsPurchasedItems() throws Exception {
        String orderId = orders.create(new CreateOrderRequest(List.of(
                new CreateOrderRequest.Item("tee-01", "M", 1),
                new CreateOrderRequest.Item("tee-02", "L", 1)))).orderId();
        Checkout checkout = checkout(orderId);
        when(toss.confirm(any())).thenReturn(succeeded(47_000));

        mvc.perform(post("/payments/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(confirmJson(checkout, "catalog-key", 47_000)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(47000))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.payment.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.items[0].productId").value("tee-01"))
                .andExpect(jsonPath("$.items[1].productId").value("tee-02"));
    }

    @Test
    void orderItemsHaveIndependentIdsAndKeepTheirOrder() throws Exception {
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
    void catalogPriceChangesDoNotChangePastOrderSnapshots() throws Exception {
        String oldOrderId = order();
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
    void discontinuedProductsRemainAvailableInPastOrders() throws Exception {
        String orderId = order();
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
    void invalidCatalogSelectionsCannotCreateOrders() throws Exception {
        for (String item : List.of(
                "{\"productId\":\"tee-01\",\"size\":\"XXL\",\"quantity\":1}",
                "{\"productId\":\"tee-01\",\"size\":\"M\",\"quantity\":11}",
                "{\"productId\":\"missing\",\"size\":\"M\",\"quantity\":1}")) {
            mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"items\":[" + item + "]}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("""
                {"items":[
                  {"productId":"tee-01","size":"M","quantity":1},
                  {"productId":"tee-01","size":"M","quantity":1}
                ]}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CART"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM purchase_orders", Long.class)).isZero();
    }

    @Test
    void orderAndItemsRollbackTogetherWhenAnItemCannotBeSaved() {
        jdbc.execute("""
                CREATE FUNCTION reject_order_item() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'item storage unavailable'; END $$
                """);
        jdbc.execute("CREATE TRIGGER reject_order_item BEFORE INSERT ON purchase_order_items "
                + "FOR EACH ROW EXECUTE FUNCTION reject_order_item()");
        try {
            assertThatThrownBy(this::order).isInstanceOf(DataAccessException.class);
        } finally {
            jdbc.execute("DROP TRIGGER reject_order_item ON purchase_order_items");
            jdbc.execute("DROP FUNCTION reject_order_item()");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM purchase_orders", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM purchase_order_items", Long.class)).isZero();
    }

    @Test
    void preparationCommitsBeforeCallingToss() throws Exception {
        Checkout checkout = checkout(order());
        when(toss.confirm(any())).thenAnswer(invocation -> {
            Payment sent = invocation.getArgument(0);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(paymentStatus(checkout.orderId())).isEqualTo("PROCESSING");
            assertThat(attemptStatus(checkout.attemptId())).isEqualTo("PROCESSING");
            assertThat(sent.getRequestedAmount()).isEqualTo(19_000);
            assertThat(sent.getRequestedCurrency()).isEqualTo("KRW");
            return succeeded(19_000);
        });
        mvc.perform(post("/payments/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(confirmJson(checkout, "payment-key", 19_000)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.payment.status").value("SUCCEEDED"));
        assertThat(attemptStatus(checkout.attemptId())).isEqualTo("SUCCEEDED");
        verify(toss, never()).lookup(any());
    }

    @Test
    void wrongRequestAmountIsRejectedBeforeCallingToss() throws Exception {
        Checkout checkout = checkout(order());
        mvc.perform(post("/payments/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(confirmJson(checkout, "payment-key", 18_000)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AMOUNT_MISMATCH"));
        assertThat(paymentRepository.count()).isZero();
        assertThat(attemptStatus(checkout.attemptId())).isEqualTo("STARTED");
        verifyNoInteractions(toss);
    }

    @Test
    void confirmationRequiresAnAttempt() throws Exception {
        String orderId = order();
        mvc.perform(post("/payments/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"" + orderId + "\",\"paymentKey\":\"payment-key\",\"amount\":19000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertThat(paymentRepository.count()).isZero();
        verifyNoInteractions(toss);
    }

    @Test
    void anAttemptFromAnotherOrderCannotBeConfirmed() {
        String orderId = order();
        Checkout other = checkout(order());
        assertThatThrownBy(() -> payments.confirm(new ConfirmPaymentRequest(
                orderId, "payment-key", BigDecimal.valueOf(19_000), other.attemptId())))
                .isInstanceOf(ApiException.class);
        assertThat(paymentRepository.count()).isZero();
        assertThat(attemptStatus(other.attemptId())).isEqualTo("STARTED");
        verifyNoInteractions(toss);
    }

    @Test
    void preparationRollsBackPaymentIfAttemptUpdateFails() {
        Checkout checkout = checkout(order());
        jdbc.execute("""
                CREATE FUNCTION reject_attempt_processing() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                  IF NEW.status = 'PROCESSING' THEN RAISE EXCEPTION 'attempt storage unavailable'; END IF;
                  RETURN NEW;
                END $$
                """);
        jdbc.execute("CREATE TRIGGER reject_attempt_processing BEFORE UPDATE ON payment_attempts "
                + "FOR EACH ROW EXECUTE FUNCTION reject_attempt_processing()");
        try {
            assertThatThrownBy(() -> payments.confirm(request(checkout, "payment-key")))
                    .isInstanceOf(DataAccessException.class);
        } finally {
            jdbc.execute("DROP TRIGGER reject_attempt_processing ON payment_attempts");
            jdbc.execute("DROP FUNCTION reject_attempt_processing()");
        }
        assertThat(paymentRepository.count()).isZero();
        assertThat(attemptStatus(checkout.attemptId())).isEqualTo("STARTED");
        verifyNoInteractions(toss);
    }

    @Test
    void settlementRollsBackPaymentAttemptAndOrderTogether() {
        Checkout checkout = checkout(order());
        when(toss.confirm(any())).thenReturn(succeeded(19_000));
        jdbc.execute("""
                CREATE FUNCTION reject_order_confirmation() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                  IF NEW.status = 'CONFIRMED' THEN RAISE EXCEPTION 'order storage unavailable'; END IF;
                  RETURN NEW;
                END $$
                """);
        jdbc.execute("CREATE TRIGGER reject_order_confirmation BEFORE UPDATE ON purchase_orders "
                + "FOR EACH ROW EXECUTE FUNCTION reject_order_confirmation()");
        try {
            assertThatThrownBy(() -> payments.confirm(request(checkout, "payment-key")))
                    .isInstanceOf(DataAccessException.class);
        } finally {
            jdbc.execute("DROP TRIGGER reject_order_confirmation ON purchase_orders");
            jdbc.execute("DROP FUNCTION reject_order_confirmation()");
        }
        // The committed preparation survives a result-write failure.
        assertThat(paymentStatus(checkout.orderId())).isEqualTo("PROCESSING");
        assertThat(attemptStatus(checkout.attemptId())).isEqualTo("PROCESSING");
        assertThat(orders.get(checkout.orderId()).status().name()).isEqualTo("PENDING_PAYMENT");
        verify(toss).confirm(any());
        verify(toss, never()).lookup(any());
    }

    @Test
    void explicitDeclineDoesNotTriggerLookup() {
        Checkout checkout = checkout(order());
        when(toss.confirm(any())).thenReturn(PaymentResult.failed("REJECT_CARD_COMPANY"));
        assertThat(payments.confirm(request(checkout, "payment-key")).payment().status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(attemptStatus(checkout.attemptId())).isEqualTo("FAILED");
        assertThat(orders.get(checkout.orderId()).status().name()).isEqualTo("PENDING_PAYMENT");
        verify(toss, never()).lookup(any());
    }

    @Test
    void uncertainApprovalIsCommittedBeforeOneLookupAndCanBecomeSuccessful() {
        Checkout checkout = checkout(order());
        when(toss.confirm(any())).thenReturn(PaymentResult.unknown("PG_COMMUNICATION_ERROR"));
        when(toss.lookup(any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(paymentStatus(checkout.orderId())).isEqualTo("UNKNOWN");
            assertThat(attemptStatus(checkout.attemptId())).isEqualTo("PROCESSING");
            return succeeded(19_000);
        });
        assertThat(payments.confirm(request(checkout, "payment-key")).payment().status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(attemptStatus(checkout.attemptId())).isEqualTo("SUCCEEDED");
        verify(toss).lookup(any());
    }

    @ParameterizedTest
    @MethodSource("reviewResults")
    void anomalousLookupRequiresReviewAndBlocksAnotherApproval(PaymentResult lookupResult) throws Exception {
        Checkout checkout = checkout(order());
        when(toss.confirm(any())).thenReturn(PaymentResult.unknown("PG_RESPONSE_MISMATCH"));
        when(toss.lookup(any())).thenReturn(lookupResult);
        mvc.perform(post("/payments/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(confirmJson(checkout, "payment-key", 19_000)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.payment.status").value("REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.payment.errorCode").value(lookupResult.errorCode()));
        Payment saved = paymentRepository.findByPaymentKey("payment-key").orElseThrow();
        assertThat(saved.getPgAmount()).isEqualByComparingTo(lookupResult.pgAmount());
        assertThat(saved.getPgCurrency()).isEqualTo(lookupResult.pgCurrency());
        assertThat(attemptStatus(checkout.attemptId())).isEqualTo("REVIEW_REQUIRED");
        assertThatThrownBy(() -> attempts.start(checkout.orderId())).isInstanceOf(ApiException.class);
        verify(toss).confirm(any());
        verify(toss).lookup(any());
    }

    private static Stream<PaymentResult> reviewResults() {
        return Stream.of(
                new PaymentResult(PaymentStatus.REVIEW_REQUIRED, "DONE", "PG_AMOUNT_MISMATCH",
                        Instant.parse("2026-09-11T01:00:00Z"), BigDecimal.valueOf(18_000), "KRW"),
                new PaymentResult(PaymentStatus.REVIEW_REQUIRED, "DONE", "PG_CURRENCY_MISMATCH",
                        Instant.parse("2026-09-11T01:00:00Z"), BigDecimal.valueOf(19_000), "USD"),
                new PaymentResult(PaymentStatus.REVIEW_REQUIRED, "DONE", "PG_UNSUPPORTED_METHOD",
                        Instant.parse("2026-09-11T01:00:00Z"), BigDecimal.valueOf(19_000), "KRW"),
                new PaymentResult(PaymentStatus.REVIEW_REQUIRED, "CANCELED", "PG_UNEXPECTED_STATUS",
                        Instant.parse("2026-09-11T01:00:00Z"), BigDecimal.valueOf(19_000), "KRW"));
    }

    @Test
    void unresolvedLookupNeedsReviewAndSubsequentReadsDoNotCallToss() throws Exception {
        Checkout checkout = checkout(order());
        when(toss.confirm(any())).thenReturn(PaymentResult.unknown("PG_COMMUNICATION_ERROR"));
        when(toss.lookup(any())).thenReturn(PaymentResult.unknown("PG_LOOKUP_ERROR"));
        mvc.perform(post("/payments/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(confirmJson(checkout, "payment-key", 19_000)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.payment.status").value("REVIEW_REQUIRED"));
        mvc.perform(get("/orders/{id}", checkout.orderId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.status").value("REVIEW_REQUIRED"));
        payments.confirm(request(checkout, "payment-key"));
        verify(toss).confirm(any());
        verify(toss).lookup(any());
    }

    @Test
    void concurrentDuplicateDoesNotRepeatConfirmationOrHoldOrderLockDuringHttp() throws Exception {
        Checkout checkout = checkout(order());
        Checkout competing = checkout(checkout.orderId());
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(toss.confirm(any())).thenAnswer(invocation -> {
            entered.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return succeeded(19_000);
        });
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> payments.confirm(request(checkout, "payment-key")));
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                var duplicate = executor.submit(() -> payments.confirm(request(checkout, "payment-key")));
                assertThat(duplicate.get(3, TimeUnit.SECONDS).payment().status()).isEqualTo(PaymentStatus.PROCESSING);
                var rejected = executor.submit(() -> assertThatThrownBy(
                        () -> payments.confirm(request(competing, "competing-key"))).isInstanceOf(ApiException.class));
                rejected.get(3, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
            assertThat(first.get(10, TimeUnit.SECONDS).payment().status()).isEqualTo(PaymentStatus.SUCCEEDED);
        }
        assertThat(paymentRepository.count()).isEqualTo(1);
        verify(toss).confirm(any());
    }

    @Test
    void closingWindowIsStoredWithoutPaymentAndOrderCanBeRetried() {
        Checkout first = checkout(order());
        attempts.authenticationResult(first.attemptId(),
                new PaymentAttemptService.AuthenticationResult(PaymentAttemptStatus.AUTH_CANCELED, "WINDOW_CLOSED"));
        assertThat(orders.get(first.orderId()).status().name()).isEqualTo("PENDING_PAYMENT");
        assertThat(orders.get(first.orderId()).latestAttempt().status()).isEqualTo(PaymentAttemptStatus.AUTH_CANCELED);
        assertThat(paymentRepository.count()).isZero();

        Checkout retry = checkout(first.orderId());
        when(toss.confirm(any())).thenReturn(succeeded(19_000));
        assertThat(payments.confirm(request(retry, "retry-key")).status().name()).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForList("SELECT status FROM payment_attempts WHERE order_id = ? ORDER BY started_at",
                String.class, first.orderId())).containsExactly("AUTH_CANCELED", "SUCCEEDED");
        assertThatThrownBy(() -> attempts.start(first.orderId())).isInstanceOf(ApiException.class);
    }

    @Test
    void failedApprovalPreservesHistoryAndAllowsNewAttemptForSameOrder() {
        Checkout first = checkout(order());
        when(toss.confirm(any())).thenReturn(PaymentResult.failed("REJECT_CARD_COMPANY"), succeeded(19_000));
        assertThat(payments.confirm(request(first, "failed-key")).payment().status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(orders.get(first.orderId()).status().name()).isEqualTo("PENDING_PAYMENT");

        Checkout retry = checkout(first.orderId());
        assertThat(payments.confirm(request(retry, "success-key")).payment().status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(jdbc.queryForList("SELECT status FROM payments WHERE order_id = ? ORDER BY created_at",
                String.class, first.orderId())).containsExactly("FAILED", "SUCCEEDED");
        assertThat(paymentRepository.count()).isEqualTo(2);
        assertThatThrownBy(() -> attempts.start(first.orderId())).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> payments.confirm(request(first, "third-key"))).isInstanceOf(ApiException.class);
    }

    @Test
    void databaseRejectsPaymentLinkedToAttemptFromDifferentOrder() {
        String orderId = order();
        Checkout other = checkout(order());
        assertThatThrownBy(() -> insertPayment(orderId, other.attemptId(), "payment-key"))
                .isInstanceOf(DataAccessException.class);
        assertThat(paymentRepository.count()).isZero();
    }

    @Test
    void databaseAllowsOnlyOneLivePaymentPerOrder() {
        Checkout first = checkout(order());
        Checkout second = checkout(first.orderId());
        insertPayment(first.orderId(), first.attemptId(), "first-key");
        assertThatThrownBy(() -> insertPayment(second.orderId(), second.attemptId(), "second-key"))
                .isInstanceOf(DataAccessException.class);
        assertThat(paymentRepository.count()).isEqualTo(1);
    }

    @Test
    void databaseRejectsSuccessWithoutMatchingApprovalEvidence() {
        Checkout checkout = checkout(order());
        insertPayment(checkout.orderId(), checkout.attemptId(), "payment-key");
        for (String evidence : List.of(
                "pg_status = 'DONE', pg_amount = 18000, pg_currency = 'KRW'",
                "pg_status = 'DONE', pg_amount = 19000, pg_currency = 'USD'",
                "pg_status = 'DONE', pg_amount = NULL, pg_currency = 'KRW'",
                "pg_status = 'DONE', pg_amount = 19000, pg_currency = NULL",
                "pg_status = NULL, pg_amount = 19000, pg_currency = 'KRW'")) {
            assertThatThrownBy(() -> jdbc.update("UPDATE payments SET status = 'SUCCEEDED', "
                    + "approved_at = now(), checked_at = now(), " + evidence + " WHERE order_id = ?", checkout.orderId()))
                    .isInstanceOf(DataAccessException.class);
        }
        assertThat(paymentStatus(checkout.orderId())).isEqualTo("PROCESSING");
    }

    private String order() {
        return orders.create(new CreateOrderRequest(List.of(
                new CreateOrderRequest.Item("tee-01", "M", 1)))).orderId();
    }

    private Checkout checkout(String orderId) {
        return new Checkout(orderId, attempts.start(orderId).id());
    }

    private String paymentStatus(String orderId) {
        return jdbc.queryForObject("SELECT status FROM payments WHERE order_id = ?", String.class, orderId);
    }

    private String attemptStatus(String attemptId) {
        return jdbc.queryForObject("SELECT status FROM payment_attempts WHERE id = ?", String.class, attemptId);
    }

    private void insertPayment(String orderId, String attemptId, String paymentKey) {
        jdbc.update("""
                INSERT INTO payments (id, order_id, attempt_id, payment_key, requested_amount,
                                      requested_currency, status, created_at)
                VALUES (?, ?, ?, ?, 19000, 'KRW', 'PROCESSING', now())
                """, UUID.randomUUID().toString(), orderId, attemptId, paymentKey);
    }

    private static PaymentResult succeeded(long amount) {
        return new PaymentResult(PaymentStatus.SUCCEEDED, "DONE", null,
                Instant.parse("2026-09-11T01:00:00Z"), BigDecimal.valueOf(amount), "KRW");
    }

    private static ConfirmPaymentRequest request(Checkout checkout, String key) {
        return new ConfirmPaymentRequest(checkout.orderId(), key, BigDecimal.valueOf(19_000), checkout.attemptId());
    }

    private static String confirmJson(Checkout checkout, String key, long amount) {
        return "{\"orderId\":\"" + checkout.orderId() + "\",\"paymentKey\":\"" + key + "\",\"amount\":"
                + amount + ",\"attemptId\":\"" + checkout.attemptId() + "\"}";
    }

    private record Checkout(String orderId, String attemptId) {}
}
