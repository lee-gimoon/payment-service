package com.example.payment.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.payment.order.api.CreateOrderRequest;
import com.example.payment.order.application.OrderService;
import com.example.payment.order.application.UnpaidOrderExpiryService;
import com.example.payment.payment.api.ConfirmPaymentRequest;
import com.example.payment.payment.application.PaymentAttemptService;
import com.example.payment.payment.application.PaymentService;
import com.example.payment.payment.domain.PaymentResult;
import com.example.payment.payment.infrastructure.toss.TossPaymentClient;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** 미결제 주문 자동 취소. 스케줄러는 끄고 취소 작업을 직접 호출한다. 기한은 설정값으로 2시간으로 줄였다. */
@SpringBootTest(properties = {"payment.toss.client-key=test_gck_integration", "payment.toss.secret-key=test_gsk_integration",
        "payment.toss.payment-method-variant-key=CARD_ONLY", "payment.toss.agreement-variant-key=TERMS",
        "payment.recovery.enabled=false", "order.unpaid-expiry.enabled=false", "order.unpaid-expiry.after=PT2H"})
@AutoConfigureMockMvc
@Testcontainers
class UnpaidOrderExpiryIntegrationTest {
    private static final String CUSTOMER = "customer-1";
    private static final String ADDRESS = "address-customer-1";

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
    @Autowired UnpaidOrderExpiryService expiry;
    @Autowired PaymentAttemptService attempts;
    @Autowired PaymentService payments;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean TossPaymentClient toss;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE TABLE payments, payment_attempts, purchase_orders, customer_addresses CASCADE");
        jdbc.update("UPDATE product_stocks SET quantity = 20");
        jdbc.update("""
                INSERT INTO customer_addresses (id, customer_id, label, recipient_name, phone, postal_code, address,
                    address_detail, is_default, created_at, updated_at)
                VALUES (?, ?, '집', '김모도', '01012345678', '06236', '서울 강남구 테헤란로 123', '101호', true, now(), now())
                """, ADDRESS, CUSTOMER);
    }

    @Test
    void onlyUnpaidOrdersPastTheDeadlineAreCanceled() {
        String expired = order();
        String fresh = order();
        String closedWindowLongAgo = order();
        attempts.start(closedWindowLongAgo, CUSTOMER);
        String authenticating = order();
        attempts.start(authenticating, CUSTOMER);
        String unknown = orderWithApproval(PaymentResult.unknown("TIMEOUT"));
        String paid = orderWithApproval(new PaymentResult(PaymentResult.Outcome.SUCCEEDED, "DONE", null,
                Instant.parse("2026-10-04T01:00:00Z"), BigDecimal.valueOf(19_000), "KRW"));
        for (String orderId : List.of(expired, closedWindowLongAgo, authenticating, unknown, paid)) {
            createdHoursAgo(orderId, 3);
        }
        createdHoursAgo(fresh, 1);
        // 31분 전에 연 결제창은 토스 인증이 이미 만료됐다. 방금 연 결제창은 고객이 아직 인증 중일 수 있다.
        jdbc.update("UPDATE payment_attempts SET started_at = now() - interval '31 minutes' WHERE order_id = ?",
                closedWindowLongAgo);
        Integer stockBefore = stock();

        assertThat(expiry.cancelExpired()).isEqualTo(2);

        assertThat(orderStatus(expired)).isEqualTo("CANCELED");
        assertThat(orderStatus(closedWindowLongAgo)).isEqualTo("CANCELED");
        assertThat(orderStatus(fresh)).isEqualTo("PENDING_PAYMENT");
        assertThat(orderStatus(authenticating)).isEqualTo("PENDING_PAYMENT");
        assertThat(orderStatus(unknown)).isEqualTo("PAYMENT_IN_PROGRESS");
        assertThat(orderStatus(paid)).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM purchase_orders WHERE canceled_at IS NOT NULL", Long.class))
                .isEqualTo(2);
        // 결제 대기 주문은 재고를 잡고 있지 않으므로 취소해도 재고는 그대로다.
        assertThat(stock()).isEqualTo(stockBefore);
        // 이미 취소한 주문은 다시 고르지 않는다.
        assertThat(expiry.cancelExpired()).isZero();
    }

    @Test
    void canceledOrderIsShownAsCanceledAndCannotBePaid() throws Exception {
        String orderId = order();
        String attemptId = attempts.start(orderId, CUSTOMER).id();
        createdHoursAgo(orderId, 3);
        jdbc.update("UPDATE payment_attempts SET started_at = now() - interval '31 minutes' WHERE id = ?", attemptId);
        assertThat(expiry.cancelExpired()).isEqualTo(1);

        mvc.perform(get("/orders/{id}", orderId).with(customer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.payment.status").value("CANCELED"))
                .andExpect(jsonPath("$.payment.message").value(containsString("취소")))
                .andExpect(jsonPath("$.canceledAt").isNotEmpty())
                .andExpect(jsonPath("$.delivery").doesNotExist());
        mvc.perform(get("/me/orders").with(customer()))
                .andExpect(jsonPath("$[0].status").value("CANCELED"));

        mvc.perform(post("/orders/{id}/payment-attempts", orderId).with(customer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_CANCELED"));
        // 취소 전에 연 결제창에서 인증을 마치고 돌아와도 승인을 요청하지 않는다.
        mvc.perform(post("/payments/confirm").with(customer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"" + orderId + "\",\"paymentKey\":\"key-late\",\"amount\":19000,"
                                + "\"attemptId\":\"" + attemptId + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_CANCELED"));
        verifyNoInteractions(toss);
        assertThat(jdbc.queryForObject("SELECT status FROM payment_attempts WHERE id = ?", String.class, attemptId))
                .isEqualTo("STARTED");
    }

    @Test
    void databaseKeepsCancellationConsistent() {
        String unpaid = order();
        String unknown = orderWithApproval(PaymentResult.unknown("TIMEOUT"));

        assertThatThrownBy(() -> jdbc.update("UPDATE purchase_orders SET status = 'CANCELED' WHERE id = ?", unpaid))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE purchase_orders SET canceled_at = now() WHERE id = ?", unpaid))
                .isInstanceOf(DataAccessException.class);
        // 승인 슬롯을 가진 주문은 취소 상태가 될 수 없다.
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE purchase_orders SET status = 'CANCELED', canceled_at = now() WHERE id = ?", unknown))
                .isInstanceOf(DataAccessException.class);
        jdbc.update("UPDATE purchase_orders SET status = 'CANCELED', canceled_at = now() WHERE id = ?", unpaid);
        assertThat(orderStatus(unpaid)).isEqualTo("CANCELED");
    }

    private String order() {
        return orders.create(new CreateOrderRequest(List.of(new CreateOrderRequest.Item("tee-01", "M", 1)), ADDRESS, null),
                CUSTOMER).orderId();
    }

    private String orderWithApproval(PaymentResult result) {
        String orderId = order();
        String attemptId = attempts.start(orderId, CUSTOMER).id();
        when(toss.confirm(any())).thenReturn(result);
        // 결과를 모르면 승인 직후 한 번 더 조회한다. 그래도 모르면 주문은 결제 진행 중으로 남는다.
        when(toss.lookup(any())).thenReturn(result);
        payments.confirm(new ConfirmPaymentRequest(orderId, "key-" + attemptId, BigDecimal.valueOf(19_000), attemptId),
                CUSTOMER);
        return orderId;
    }

    private void createdHoursAgo(String orderId, int hours) {
        jdbc.update("UPDATE purchase_orders SET created_at = now() - make_interval(hours => ?) WHERE id = ?", hours, orderId);
    }

    private String orderStatus(String orderId) {
        return jdbc.queryForObject("SELECT status FROM purchase_orders WHERE id = ?", String.class, orderId);
    }

    private Integer stock() {
        return jdbc.queryForObject("SELECT quantity FROM product_stocks WHERE product_id = 'tee-01' AND size = 'M'",
                Integer.class);
    }

    private static RequestPostProcessor customer() {
        return jwt().jwt(token -> token.subject(CUSTOMER));
    }
}
