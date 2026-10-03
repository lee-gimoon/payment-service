package com.example.payment.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.payment.config.SecurityConfiguration;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** 관리자 주문 관리: 결제 완료 주문의 송장 등록, 배송 완료, 고객 화면의 배송 단계. */
@SpringBootTest(properties = {"payment.toss.client-key=test_gck_integration", "payment.toss.secret-key=test_gsk_integration",
        "payment.toss.payment-method-variant-key=CARD_ONLY", "payment.toss.agreement-variant-key=TERMS",
        "payment.recovery.enabled=false"})
@AutoConfigureMockMvc
@Testcontainers
class OrderShippingIntegrationTest {
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
    void paidOrdersWaitForAShipmentOldestFirst() throws Exception {
        String unpaid = order();
        String older = paidOrder();
        String newer = paidOrder();
        jdbc.update("UPDATE purchase_orders SET paid_at = now() - interval '2 minutes' WHERE id = ?", older);
        jdbc.update("UPDATE purchase_orders SET paid_at = now() - interval '1 minute' WHERE id = ?", newer);

        mvc.perform(get("/admin/orders").param("delivery", "PREPARING").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].orderId").value(older))
                .andExpect(jsonPath("$[0].recipientName").value("김모도"))
                .andExpect(jsonPath("$[0].delivery.status").value("PREPARING"))
                .andExpect(jsonPath("$[1].orderId").value(newer));
        // 전체 목록은 최근 결제 순서이고 결제 전 주문은 보이지 않는다.
        mvc.perform(get("/admin/orders").with(admin()))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].orderId").value(newer));
        mvc.perform(get("/admin/orders/summary").with(admin()))
                .andExpect(jsonPath("$.preparing").value(2))
                .andExpect(jsonPath("$.shipping").value(0));

        mvc.perform(get("/orders/{id}", older).with(customer()))
                .andExpect(jsonPath("$.delivery.status").value("PREPARING"))
                .andExpect(jsonPath("$.delivery.trackingNumber").doesNotExist());
        mvc.perform(get("/orders/{id}", unpaid).with(customer()))
                .andExpect(jsonPath("$.delivery").doesNotExist());
    }

    @Test
    void shippingThenDeliveringIsShownToTheCustomer() throws Exception {
        String orderId = paidOrder();

        ship(orderId, "CJ", "123456789012")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delivery.status").value("SHIPPED"))
                .andExpect(jsonPath("$.delivery.carrier").value("CJ"))
                .andExpect(jsonPath("$.shipping.address").value("서울 강남구 테헤란로 123"))
                .andExpect(jsonPath("$.items[0].productId").value("tee-01"));
        String shippedAt = jdbc.queryForObject("SELECT shipped_at::text FROM shipments WHERE order_id = ?", String.class, orderId);

        // 배송 중에는 잘못 넣은 송장을 고칠 수 있고, 출고 시각은 그대로다.
        ship(orderId, "HANJIN", "987654321098")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delivery.carrier").value("HANJIN"))
                .andExpect(jsonPath("$.delivery.trackingNumber").value("987654321098"));
        assertThat(jdbc.queryForObject("SELECT shipped_at::text FROM shipments WHERE order_id = ?", String.class, orderId))
                .isEqualTo(shippedAt);

        mvc.perform(get("/orders/{id}", orderId).with(customer()))
                .andExpect(jsonPath("$.delivery.status").value("SHIPPED"))
                .andExpect(jsonPath("$.delivery.trackingNumber").value("987654321098"));
        mvc.perform(get("/me/orders").with(customer()))
                .andExpect(jsonPath("$[0].delivery").value("SHIPPED"));
        mvc.perform(get("/admin/orders").param("delivery", "SHIPPED").with(admin()))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/admin/orders").param("delivery", "PREPARING").with(admin()))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/admin/orders/summary").with(admin()))
                .andExpect(jsonPath("$.preparing").value(0))
                .andExpect(jsonPath("$.shipping").value(1));

        mvc.perform(post("/admin/orders/{id}/shipment/delivered", orderId).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delivery.status").value("DELIVERED"))
                .andExpect(jsonPath("$.delivery.deliveredAt").isNotEmpty());
        mvc.perform(get("/orders/{id}", orderId).with(customer()))
                .andExpect(jsonPath("$.delivery.status").value("DELIVERED"));

        ship(orderId, "CJ", "111122223333")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DELIVERY_COMPLETED"));
        // 같은 완료 요청이 다시 와도 결과는 그대로다.
        mvc.perform(post("/admin/orders/{id}/shipment/delivered", orderId).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delivery.status").value("DELIVERED"));
        mvc.perform(get("/admin/orders").param("delivery", "DELIVERED").with(admin()))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void onlyPaidOrdersWithAnAddressCanBeShipped() throws Exception {
        String unpaid = order();
        ship(unpaid, "CJ", "123456789012")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_PAID"));

        String paid = paidOrder();
        mvc.perform(post("/admin/orders/{id}/shipment/delivered", paid).with(admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SHIPMENT_NOT_REGISTERED"));
        for (String invalid : List.of(
                "{\"carrier\":\"CJ\",\"trackingNumber\":\"1234-5678\"}",
                "{\"carrier\":\"CJ\",\"trackingNumber\":\"1234567\"}",
                "{\"carrier\":\"DHL\",\"trackingNumber\":\"123456789012\"}",
                "{\"trackingNumber\":\"123456789012\"}")) {
            mvc.perform(put("/admin/orders/{id}/shipment", paid).with(admin())
                            .contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        mvc.perform(get("/admin/orders").param("delivery", "LOST").with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        ship("missing-order", "CJ", "123456789012")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));

        // 배송지를 받기 전에 결제된 주문은 보낼 곳이 없다.
        jdbc.update("UPDATE purchase_orders SET shipping_recipient_name = NULL, shipping_phone = NULL, "
                + "shipping_postal_code = NULL, shipping_address = NULL, shipping_address_detail = NULL WHERE id = ?", paid);
        ship(paid, "CJ", "123456789012")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SHIPPING_ADDRESS_REQUIRED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM shipments", Long.class)).isZero();
    }

    @Test
    void customersCannotManageOrders() throws Exception {
        String orderId = paidOrder();
        mvc.perform(get("/admin/orders").with(customer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(put("/admin/orders/{id}/shipment", orderId).with(customer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"carrier\":\"CJ\",\"trackingNumber\":\"123456789012\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/orders")).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM shipments", Long.class)).isZero();
    }

    @Test
    void databaseKeepsOneShipmentPerOrderAndDeliveryTimeWithDeliveredStatus() throws Exception {
        String orderId = paidOrder();
        ship(orderId, "CJ", "123456789012").andExpect(status().isOk());

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO shipments (id, order_id, status, carrier, tracking_number, shipped_at)
                VALUES (gen_random_uuid()::text, ?, 'SHIPPED', 'CJ', '123456789012', now())
                """, orderId)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE shipments SET status = 'DELIVERED' WHERE order_id = ?", orderId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE shipments SET tracking_number = '12-34' WHERE order_id = ?", orderId))
                .isInstanceOf(DataAccessException.class);
    }

    private String order() {
        return orders.create(new CreateOrderRequest(List.of(new CreateOrderRequest.Item("tee-01", "M", 1)), ADDRESS, null),
                CUSTOMER).orderId();
    }

    private String paidOrder() {
        String orderId = order();
        String attemptId = attempts.start(orderId, CUSTOMER).id();
        when(toss.confirm(any())).thenReturn(new PaymentResult(PaymentResult.Outcome.SUCCEEDED, "DONE", null,
                Instant.parse("2026-10-04T01:00:00Z"), BigDecimal.valueOf(19_000), "KRW"));
        payments.confirm(new ConfirmPaymentRequest(orderId, "key-" + attemptId, BigDecimal.valueOf(19_000), attemptId),
                CUSTOMER);
        assertThat(jdbc.queryForObject("SELECT status FROM purchase_orders WHERE id = ?", String.class, orderId))
                .isEqualTo("PAID");
        return orderId;
    }

    private ResultActions ship(String orderId, String carrier, String trackingNumber) throws Exception {
        return mvc.perform(put("/admin/orders/{id}/shipment", orderId).with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"carrier\":\"" + carrier + "\",\"trackingNumber\":\"" + trackingNumber + "\"}"));
    }

    private static RequestPostProcessor customer() {
        return jwt().jwt(token -> token.subject(CUSTOMER));
    }

    private static RequestPostProcessor admin() {
        return jwt().jwt(token -> token.subject("admin-1"))
                .authorities(new SimpleGrantedAuthority("ROLE_" + SecurityConfiguration.SHOP_ADMIN));
    }
}
