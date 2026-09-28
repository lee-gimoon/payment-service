package com.example.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** V1 스키마에 쌓인 주문·시도·거래가 이후 마이그레이션에서 시도, 주문 슬롯, 성공한 결제로 옮겨지는지 확인한다. */
@Testcontainers
class MigrationUpgradeTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @Test
    void existingPaymentsMoveIntoAttemptsOrderSlotsAndCompletedPayments() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).target("1").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        order(jdbc, "order-paid", "CONFIRMED");
        attempt(jdbc, "a-paid", "order-paid", "SUCCEEDED", "now() - interval '1 minute'");
        payment(jdbc, "a-paid", "order-paid", "key-paid", "SUCCEEDED", "'DONE'", "now()", "19000", "'KRW'");

        order(jdbc, "order-retry", "PENDING_PAYMENT");
        attempt(jdbc, "a-closed", "order-retry", "AUTH_CANCELED", "now()");
        attempt(jdbc, "a-declined", "order-retry", "FAILED", "now()");
        payment(jdbc, "a-declined", "order-retry", "key-declined", "FAILED", "NULL", "NULL", "NULL", "NULL");

        order(jdbc, "order-unknown", "PENDING_PAYMENT");
        attempt(jdbc, "a-unknown", "order-unknown", "PROCESSING", "NULL");
        payment(jdbc, "a-unknown", "order-unknown", "key-unknown", "UNKNOWN", "NULL", "NULL", "NULL", "NULL");

        order(jdbc, "order-approving", "PENDING_PAYMENT");
        attempt(jdbc, "a-approving", "order-approving", "PROCESSING", "NULL");
        payment(jdbc, "a-approving", "order-approving", "key-approving", "PROCESSING", "NULL", "NULL", "NULL", "NULL");

        order(jdbc, "order-waiting", "PENDING_PAYMENT");
        attempt(jdbc, "a-started", "order-waiting", "STARTED", "NULL");

        Flyway.configure().dataSource(dataSource).load().migrate();

        // V2가 옛 거래 테이블을 지우고, V3가 성공한 결제만 새 payments로 다시 만든다.
        assertThat(jdbc.queryForList("SELECT attempt_id FROM payments", String.class)).containsExactly("a-paid");
        assertThat(jdbc.queryForMap("SELECT order_id, payment_key, amount, currency FROM payments"))
                .containsEntry("order_id", "order-paid").containsEntry("payment_key", "key-paid")
                .containsEntry("amount", 19000L).containsEntry("currency", "KRW");
        assertOrder(jdbc, "order-paid", "PAID", "a-paid", true);
        assertOrder(jdbc, "order-retry", "PENDING_PAYMENT", null, false);
        assertOrder(jdbc, "order-unknown", "PAYMENT_IN_PROGRESS", "a-unknown", false);
        assertOrder(jdbc, "order-approving", "PAYMENT_IN_PROGRESS", "a-approving", false);
        assertOrder(jdbc, "order-waiting", "PENDING_PAYMENT", null, false);

        assertAttempt(jdbc, "a-paid", "SUCCEEDED", "key-paid");
        assertAttempt(jdbc, "a-closed", "AUTH_CANCELED", null);
        assertAttempt(jdbc, "a-declined", "FAILED", "key-declined");
        assertAttempt(jdbc, "a-unknown", "UNKNOWN", "key-unknown");
        assertAttempt(jdbc, "a-approving", "APPROVING", "key-approving");
        assertAttempt(jdbc, "a-started", "STARTED", null);
        assertThat(jdbc.queryForMap("SELECT amount, currency, pg_status, pg_amount FROM payment_attempts WHERE id = 'a-paid'"))
                .containsEntry("amount", 19000L).containsEntry("currency", "KRW").containsEntry("pg_status", "DONE")
                .hasEntrySatisfying("pg_amount", amount -> assertThat(amount.toString()).startsWith("19000"));
    }

    private static void order(JdbcTemplate jdbc, String id, String status) {
        jdbc.update("""
                INSERT INTO purchase_orders (id, product_name, quantity, amount, currency, status, created_at)
                VALUES (?, '선데이 크루 티', 1, 19000, 'KRW', ?, now() - interval '10 minutes')
                """, id, status);
    }

    private static void attempt(JdbcTemplate jdbc, String id, String orderId, String status, String finishedAt) {
        jdbc.update("INSERT INTO payment_attempts (id, order_id, status, started_at, finished_at) "
                + "VALUES (?, ?, ?, now() - interval '5 minutes', " + finishedAt + ")", id, orderId, status);
    }

    private static void payment(JdbcTemplate jdbc, String attemptId, String orderId, String paymentKey, String status,
                                String pgStatus, String approvedAt, String pgAmount, String pgCurrency) {
        jdbc.update("INSERT INTO payments (id, order_id, attempt_id, payment_key, requested_amount, requested_currency, "
                + "status, created_at, checked_at, approved_at, pg_status, pg_amount, pg_currency) "
                + "VALUES (?, ?, ?, ?, 19000, 'KRW', ?, now() - interval '4 minutes', now(), " + approvedAt + ", "
                + pgStatus + ", " + pgAmount + ", " + pgCurrency + ")",
                "p-" + attemptId, orderId, attemptId, paymentKey, status);
    }

    private static void assertOrder(JdbcTemplate jdbc, String id, String status, String slot, boolean paid) {
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT status, approval_attempt_id, paid_at FROM purchase_orders WHERE id = ?", id);
        assertThat(row.get("status")).isEqualTo(status);
        assertThat(row.get("approval_attempt_id")).isEqualTo(slot);
        assertThat(row.get("paid_at") != null).isEqualTo(paid);
    }

    private static void assertAttempt(JdbcTemplate jdbc, String id, String status, String paymentKey) {
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT status, payment_key, approval_requested_at FROM payment_attempts WHERE id = ?", id);
        assertThat(row.get("status")).isEqualTo(status);
        assertThat(row.get("payment_key")).isEqualTo(paymentKey);
        assertThat(row.get("approval_requested_at") != null).isEqualTo(paymentKey != null);
    }
}
