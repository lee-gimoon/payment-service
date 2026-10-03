package com.example.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.stream.Collectors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * V1 스키마에 쌓인 주문·시도·거래가 이후 마이그레이션에서 시도, 주문 슬롯, 성공한 결제로 옮겨지고,
 * 승인이 진행 중인 주문의 상품만큼 초기 재고(V7, V8)가 빠지는지 확인한다.
 */
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

        // V1은 수동 확인 대상에도 완료 시각을 기록했다.
        order(jdbc, "order-review", "PENDING_PAYMENT");
        attempt(jdbc, "a-review", "order-review", "REVIEW_REQUIRED", "now()");
        payment(jdbc, "a-review", "order-review", "key-review", "REVIEW_REQUIRED", "NULL", "NULL", "NULL", "NULL");

        // 승인이 진행 중인 주문(V2 이후 PAYMENT_IN_PROGRESS)의 상품은 V7·V8에서 재고를 이미 가져간 것으로 본다.
        item(jdbc, "order-unknown", "tee-01", "S", 2);
        item(jdbc, "order-unknown", "tee-04", "M", 2);
        item(jdbc, "order-approving", "tee-01", "S", 1);
        item(jdbc, "order-review", "tee-01", "L", 1);
        item(jdbc, "order-paid", "tee-01", "M", 1);
        item(jdbc, "order-retry", "tee-01", "XL", 3);

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
        assertOrder(jdbc, "order-review", "PAYMENT_IN_PROGRESS", "a-review", false);
        assertAttempt(jdbc, "a-review", "REVIEW_REQUIRED", "key-review");
        assertThat(jdbc.queryForList("SELECT id FROM payment_attempts WHERE finished_at IS NOT NULL ORDER BY id",
                String.class)).containsExactly("a-closed", "a-declined", "a-paid");
        // 로그인 도입 전 주문은 주인이 없다.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM purchase_orders WHERE customer_id IS NOT NULL", Long.class))
                .isZero();
        assertThat(jdbc.queryForMap("SELECT amount, currency, pg_status, pg_amount FROM payment_attempts WHERE id = 'a-paid'"))
                .containsEntry("amount", 19000L).containsEntry("currency", "KRW").containsEntry("pg_status", "DONE")
                .hasEntrySatisfying("pg_amount", amount -> assertThat(amount.toString()).startsWith("19000"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_stocks", Long.class)).isEqualTo(40);
        assertThat(stocks(jdbc, "tee-01")).isEqualTo(Map.of("S", 17, "M", 20, "L", 19, "XL", 20));
        assertThat(stocks(jdbc, "tee-10")).isEqualTo(Map.of("S", 3, "M", 3, "L", 3, "XL", 0));
        assertThat(stocks(jdbc, "tee-02")).isEqualTo(Map.of("S", 20, "M", 20, "L", 20, "XL", 20));
        // V8은 tee-04~09만 1~10장으로 바꾸고, 진행 중인 승인이 잡은 수량은 다시 뺀다.
        assertThat(stocks(jdbc, "tee-04")).isEqualTo(Map.of("S", 8, "M", 8, "L", 10, "XL", 1));
        assertThat(stocks(jdbc, "tee-09")).isEqualTo(Map.of("S", 1, "M", 3, "L", 9, "XL", 5));
    }

    private static void order(JdbcTemplate jdbc, String id, String status) {
        jdbc.update("""
                INSERT INTO purchase_orders (id, product_name, quantity, amount, currency, status, created_at)
                VALUES (?, '선데이 크루 티', 1, 19000, 'KRW', ?, now() - interval '10 minutes')
                """, id, status);
    }

    private static void item(JdbcTemplate jdbc, String orderId, String productId, String size, int quantity) {
        jdbc.update("INSERT INTO purchase_order_items (id, order_id, line_number, product_id, product_name, size, "
                + "unit_price, quantity) SELECT gen_random_uuid()::text, ?, "
                + "(SELECT count(*) FROM purchase_order_items WHERE order_id = ?), ?, name, ?, price, ? "
                + "FROM products WHERE id = ?",
                orderId, orderId, productId, size, quantity, productId);
    }

    private static Map<String, Integer> stocks(JdbcTemplate jdbc, String productId) {
        return jdbc.queryForList("SELECT size, quantity FROM product_stocks WHERE product_id = ?", productId).stream()
                .collect(Collectors.toMap(row -> (String) row.get("size"), row -> (Integer) row.get("quantity")));
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
