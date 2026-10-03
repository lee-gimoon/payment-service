package com.example.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.payment.api.error.ApiException;
import com.example.payment.config.SecurityConfiguration;
import com.example.payment.order.CreateOrderRequest;
import com.example.payment.order.OrderResponse.PaymentState;
import com.example.payment.order.OrderService;
import com.example.payment.payment.api.ConfirmPaymentRequest;
import com.example.payment.payment.application.PaymentAttemptService;
import com.example.payment.payment.application.PaymentPreparationService;
import com.example.payment.payment.application.PaymentRecoveryService;
import com.example.payment.payment.application.PaymentService;
import com.example.payment.payment.domain.ApprovalRequest;
import com.example.payment.payment.domain.Payment;
import com.example.payment.payment.domain.PaymentAttempt;
import com.example.payment.payment.domain.PaymentAttemptStatus;
import com.example.payment.payment.domain.PaymentResult;
import com.example.payment.payment.domain.PaymentResult.Outcome;
import com.example.payment.payment.infrastructure.toss.TossPaymentClient;
import com.example.payment.payment.persistence.PaymentAttemptRepository;
import com.example.payment.payment.persistence.PaymentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {"payment.toss.client-key=test_gck_integration", "payment.toss.secret-key=test_gsk_integration",
        "payment.toss.payment-method-variant-key=CARD_ONLY", "payment.toss.agreement-variant-key=TERMS",
        "payment.recovery.enabled=false"})
@AutoConfigureMockMvc
@Testcontainers
@Import(PaymentIntegrationTest.AdminProbe.class)
class PaymentIntegrationTest {
    private static final String CUSTOMER = "customer-1";
    private static final String OTHER_CUSTOMER = "customer-2";
    // 테스트마다 두 회원에게 저장 배송지를 하나씩 만든다. 주문은 내 배송지를 골라야 만들 수 있다.
    private static final String CUSTOMER_ADDRESS = "address-customer-1";
    private static final String OTHER_CUSTOMER_ADDRESS = "address-customer-2";

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
    @Autowired PaymentPreparationService preparation;
    @Autowired PaymentRecoveryService recovery;
    @Autowired PaymentAttemptRepository attemptRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean TossPaymentClient toss;
    @MockitoBean JwtDecoder jwtDecoder;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE TABLE payments, payment_attempts, purchase_orders, customer_addresses CASCADE");
        // 테스트마다 모든 사이즈의 재고를 같은 수량으로 맞춘다. 초기 재고 값은 MigrationUpgradeTest가 확인한다.
        jdbc.update("UPDATE product_stocks SET quantity = 20");
        saveAddress(CUSTOMER_ADDRESS, CUSTOMER);
        saveAddress(OTHER_CUSTOMER_ADDRESS, OTHER_CUSTOMER);
    }

    @Test
    void freshDatabaseAppliesAllMigrations() {
        assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL "
                + "ORDER BY installed_rank", String.class)).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11");
        assertThat(columns("payment_attempts")).contains("amount", "currency", "payment_key", "approval_requested_at",
                "last_checked_at", "pg_status", "pg_approved_at", "pg_amount", "pg_currency");
        assertThat(columns("purchase_orders")).contains("approval_attempt_id", "paid_at", "customer_id",
                "shipping_recipient_name", "shipping_phone", "shipping_postal_code", "shipping_address",
                "shipping_address_detail", "shipping_memo");
        assertThat(columns("payments")).containsExactlyInAnyOrder("id", "order_id", "attempt_id", "payment_key",
                "amount", "currency", "approved_at", "created_at");
        assertThat(columns("product_stocks")).containsExactlyInAnyOrder("product_id", "size", "quantity");
    }

    @Test
    void catalogOrderUsesServerPricesAndStoresSelectedOptions() throws Exception {
        mvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10))
                .andExpect(jsonPath("$[0].id").value("tee-01"))
                .andExpect(jsonPath("$[0].price").value(19000))
                .andExpect(jsonPath("$[0].sizes.length()").value(4))
                .andExpect(jsonPath("$[0].sizes[1].size").value("M"))
                .andExpect(jsonPath("$[0].sizes[1].soldOut").value(false))
                .andExpect(jsonPath("$[0].sizes[1].remaining").value(10));

        mvc.perform(post("/orders").with(signedIn()).contentType(MediaType.APPLICATION_JSON).content("""
                {"addressId":"address-customer-1","items":[
                  {"productId":"tee-01","size":"M","quantity":2},
                  {"productId":"tee-04","size":"L","quantity":1}
                ]}
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(65000))
                .andExpect(jsonPath("$.quantity").value(3))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.payment.status").value("READY"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(19000))
                .andExpect(jsonPath("$.items[1].size").value("L"));

        assertThat(jdbc.queryForObject("SELECT amount FROM purchase_orders", Long.class)).isEqualTo(65000);
        assertThat(jdbc.queryForObject("SELECT customer_id FROM purchase_orders", String.class)).isEqualTo(CUSTOMER);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM purchase_order_items", Long.class)).isEqualTo(2);
        // 주문 생성은 재고를 확인만 하고 가져가지 않는다.
        assertThat(stock("tee-01", "M")).isEqualTo(20);
    }

    @Test
    void soldOutSizesAreShownAndCannotBeOrdered() throws Exception {
        setStock("tee-01", "M", 0);
        setStock("tee-01", "L", 1);
        mvc.perform(get("/products/tee-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sizes[1].size").value("M"))
                .andExpect(jsonPath("$.sizes[1].soldOut").value(true))
                .andExpect(jsonPath("$.sizes[1].remaining").value(0))
                .andExpect(jsonPath("$.sizes[2].soldOut").value(false))
                .andExpect(jsonPath("$.sizes[2].remaining").value(1))
                // 남은 수량은 한 옵션의 주문 한도(10장)까지만 알린다.
                .andExpect(jsonPath("$.sizes[0].remaining").value(10));

        for (String item : List.of(
                "{\"productId\":\"tee-01\",\"size\":\"M\",\"quantity\":1}",
                "{\"productId\":\"tee-01\",\"size\":\"L\",\"quantity\":2}")) {
            mvc.perform(post("/orders").with(signedIn()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"addressId\":\"address-customer-1\",\"items\":[" + item + "]}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("OUT_OF_STOCK"));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM purchase_orders", Long.class)).isZero();
        assertThatThrownBy(() -> setStock("tee-01", "L", -1)).isInstanceOf(DataAccessException.class);
    }

    @Test
    void orderKeepsACopyOfTheChosenAddressAfterTheSavedAddressChanges() throws Exception {
        String orderId = orders.create(new CreateOrderRequest(List.of(new CreateOrderRequest.Item("tee-01", "M", 1)),
                CUSTOMER_ADDRESS, "  문 앞에 놓아주세요  "), CUSTOMER).orderId();

        jdbc.update("UPDATE customer_addresses SET address = '부산 해운대구 해운대로 1', address_detail = '' WHERE id = ?",
                CUSTOMER_ADDRESS);
        jdbc.update("DELETE FROM customer_addresses WHERE id = ?", CUSTOMER_ADDRESS);

        mvc.perform(get("/orders/{id}", orderId).with(signedIn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipping.recipientName").value("김모도"))
                .andExpect(jsonPath("$.shipping.phone").value("01012345678"))
                .andExpect(jsonPath("$.shipping.postalCode").value("06236"))
                .andExpect(jsonPath("$.shipping.address").value("서울 강남구 테헤란로 123"))
                .andExpect(jsonPath("$.shipping.addressDetail").value("101호"))
                .andExpect(jsonPath("$.shipping.memo").value("문 앞에 놓아주세요"));
    }

    @Test
    void ordersNeedOneOfTheCustomersOwnAddresses() throws Exception {
        for (var invalid : List.of(
                List.of("{\"items\":[{\"productId\":\"tee-01\",\"size\":\"M\",\"quantity\":1}]}", "ADDRESS_REQUIRED"),
                List.of("{\"addressId\":\"" + OTHER_CUSTOMER_ADDRESS
                        + "\",\"items\":[{\"productId\":\"tee-01\",\"size\":\"M\",\"quantity\":1}]}", "ADDRESS_NOT_FOUND"),
                List.of("{\"addressId\":\"missing\",\"items\":[{\"productId\":\"tee-01\",\"size\":\"M\",\"quantity\":1}]}",
                        "ADDRESS_NOT_FOUND"),
                List.of("{\"addressId\":\"" + CUSTOMER_ADDRESS + "\",\"deliveryMemo\":\"" + "가".repeat(51)
                        + "\",\"items\":[{\"productId\":\"tee-01\",\"size\":\"M\",\"quantity\":1}]}", "INVALID_REQUEST"))) {
            mvc.perform(post("/orders").with(signedIn()).contentType(MediaType.APPLICATION_JSON).content(invalid.get(0)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(invalid.get(1)));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM purchase_orders", Long.class)).isZero();
    }

    @Test
    void ordersCreatedBeforeCheckoutAskedForAnAddressCannotStartAPayment() {
        String orderId = order();
        jdbc.update("UPDATE purchase_orders SET shipping_recipient_name = NULL, shipping_phone = NULL, "
                + "shipping_postal_code = NULL, shipping_address = NULL, shipping_address_detail = NULL, "
                + "shipping_memo = NULL WHERE id = ?", orderId);
        assertThatThrownBy(() -> attempts.start(orderId, CUSTOMER))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("SHIPPING_ADDRESS_REQUIRED");
        // 주소는 전부 있거나 전부 없어야 한다.
        assertThatThrownBy(() -> jdbc.update("UPDATE purchase_orders SET shipping_phone = '01012345678' WHERE id = ?",
                orderId)).isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_attempts", Long.class)).isZero();
    }

    @Test
    void ordersAndPaymentsRequireSignInButCatalogIsPublic() throws Exception {
        mvc.perform(get("/products")).andExpect(status().isOk());
        mvc.perform(get("/payment-config")).andExpect(status().isOk());
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("""
                {"addressId":"address-customer-1","items":[{"productId":"tee-01","size":"M","quantity":1}]}
                """))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        Checkout checkout = checkout(order());
        mvc.perform(get("/orders/{id}", checkout.orderId())).andExpect(status().isUnauthorized());
        mvc.perform(post("/orders/{id}/payment-attempts", checkout.orderId())).andExpect(status().isUnauthorized());
        mvc.perform(post("/payments/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(confirmJson(checkout, "payment-key", 19_000)))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM purchase_orders", Long.class)).isEqualTo(1);
        assertThat(attemptStatus(checkout.attemptId())).isEqualTo("STARTED");
        verifyNoInteractions(toss);
    }

    @Test
    void anotherCustomersOrderLooksMissing() throws Exception {
        Checkout checkout = checkout(order());
        mvc.perform(get("/orders/{id}", checkout.orderId()).with(signedIn(OTHER_CUSTOMER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        mvc.perform(post("/orders/{id}/payment-attempts", checkout.orderId()).with(signedIn(OTHER_CUSTOMER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        mvc.perform(post("/payment-attempts/{id}/authentication-result", checkout.attemptId())
                        .with(signedIn(OTHER_CUSTOMER)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"AUTH_CANCELED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ATTEMPT_NOT_FOUND"));
        mvc.perform(post("/payments/confirm").with(signedIn(OTHER_CUSTOMER)).contentType(MediaType.APPLICATION_JSON)
                        .content(confirmJson(checkout, "payment-key", 19_000)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_attempts", Long.class)).isEqualTo(1);
        assertThat(attemptStatus(checkout.attemptId())).isEqualTo("STARTED");
        assertThat(orderStatus(checkout.orderId())).isEqualTo("PENDING_PAYMENT");
        verifyNoInteractions(toss);
    }

    @Test
    void myOrdersListOnlyTheCustomersOwnOrdersNewestFirst() throws Exception {
        String older = order();
        jdbc.update("UPDATE purchase_orders SET created_at = now() - interval '1 hour' WHERE id = ?", older);
        orders.create(cart(OTHER_CUSTOMER_ADDRESS, new CreateOrderRequest.Item("tee-02", "S", 1)), OTHER_CUSTOMER);
        String newer = orders.create(cart(CUSTOMER_ADDRESS, new CreateOrderRequest.Item("tee-01", "M", 2),
                new CreateOrderRequest.Item("tee-04", "L", 1)), CUSTOMER).orderId();

        mvc.perform(get("/me/orders").with(signedIn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].orderId").value(newer))
                .andExpect(jsonPath("$[0].productName").value("선데이 크루 티 외 1종"))
                .andExpect(jsonPath("$[0].quantity").value(3))
                .andExpect(jsonPath("$[0].amount").value(65000))
                .andExpect(jsonPath("$[0].status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$[1].orderId").value(older));
        mvc.perform(get("/me/orders").with(signedIn("customer-without-orders")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shopAdminAccountsCannotOrderOrPay() throws Exception {
        // 관리자는 직원 계정이다. 상품은 볼 수 있지만 주문·결제·마이페이지는 일반 회원 계정으로만 쓴다.
        Checkout checkout = checkout(order());
        RequestPostProcessor admin = jwt().jwt(token -> token.subject("admin-1"))
                .authorities(new SimpleGrantedAuthority("ROLE_" + SecurityConfiguration.SHOP_ADMIN));

        mvc.perform(get("/products").with(admin)).andExpect(status().isOk());
        mvc.perform(post("/orders").with(admin).contentType(MediaType.APPLICATION_JSON).content("""
                {"addressId":"address-customer-1","items":[{"productId":"tee-01","size":"M","quantity":1}]}
                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/orders/{id}", checkout.orderId()).with(admin)).andExpect(status().isForbidden());
        mvc.perform(post("/orders/{id}/payment-attempts", checkout.orderId()).with(admin))
                .andExpect(status().isForbidden());
        mvc.perform(post("/payment-attempts/{id}/authentication-result", checkout.attemptId()).with(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"AUTH_CANCELED\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/payments/confirm").with(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(confirmJson(checkout, "payment-key", 19_000)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/me/orders").with(admin)).andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM purchase_orders", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_attempts", Long.class)).isEqualTo(1);
        assertThat(attemptStatus(checkout.attemptId())).isEqualTo("STARTED");
        assertThat(stock("tee-01", "M")).isEqualTo(20);
        verifyNoInteractions(toss);
    }

    @Test
    void adminApisRequireShopAdminRealmRole() throws Exception {
        // Bearer 토큰으로 보내 Keycloak realm_access.roles를 Spring 권한으로 바꾸는 설정까지 거친다.
        when(jwtDecoder.decode("customer-token")).thenReturn(keycloakToken("customer-token", "default-roles-modo-club"));
        when(jwtDecoder.decode("admin-token")).thenReturn(keycloakToken("admin-token", "default-roles-modo-club", "shop-admin"));

        mvc.perform(get("/admin/probe"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/admin/probe").header("Authorization", "Bearer customer-token"))
                .andExpect(status().isForbidden())
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/admin/probe").header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
    }

    @Test
    void ordersCreatedBeforeSignInBelongToNoCustomer() {
        String orderId = order();
        jdbc.update("UPDATE purchase_orders SET customer_id = NULL WHERE id = ?", orderId);
        assertThatThrownBy(() -> orders.get(orderId, CUSTOMER)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> attempts.start(orderId, CUSTOMER)).isInstanceOf(ApiException.class);
    }

    @Test
    void paidOrderReturnsPurchasedItems() throws Exception {
        String orderId = orders.create(cart(CUSTOMER_ADDRESS, new CreateOrderRequest.Item("tee-01", "M", 1),
                new CreateOrderRequest.Item("tee-02", "L", 1)), CUSTOMER).orderId();
        Checkout checkout = checkout(orderId);
        when(toss.confirm(any())).thenReturn(succeeded(47_000));

        mvc.perform(post("/payments/confirm").with(signedIn()).contentType(MediaType.APPLICATION_JSON)
                        .content(confirmJson(checkout, "catalog-key", 47_000)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(47000))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.payment.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.items[0].productId").value("tee-01"))
                .andExpect(jsonPath("$.items[1].productId").value("tee-02"));
    }

    @Test
    void orderItemsHaveIndependentIdsAndKeepTheirOrder() throws Exception {
        String orderId = orders.create(cart(CUSTOMER_ADDRESS, new CreateOrderRequest.Item("tee-02", "M", 1),
                new CreateOrderRequest.Item("tee-01", "S", 1)), CUSTOMER).orderId();

        assertThat(jdbc.queryForList(
                "SELECT id FROM purchase_order_items WHERE order_id = ? ORDER BY line_number", String.class, orderId))
                .hasSize(2).allSatisfy(id -> assertThat(id).hasSize(36));
        mvc.perform(get("/orders/{id}", orderId).with(signedIn()))
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
            mvc.perform(get("/orders/{id}", oldOrderId).with(signedIn()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.amount").value(19000))
                    .andExpect(jsonPath("$.items[0].unitPrice").value(19000));
            mvc.perform(post("/orders").with(signedIn()).contentType(MediaType.APPLICATION_JSON).content("""
                    {"addressId":"address-customer-1","items":[{"productId":"tee-01","size":"M","quantity":1}]}
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
            mvc.perform(post("/orders").with(signedIn()).contentType(MediaType.APPLICATION_JSON).content("""
                    {"addressId":"address-customer-1","items":[{"productId":"tee-01","size":"M","quantity":1}]}
                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
            mvc.perform(get("/orders/{id}", orderId).with(signedIn()))
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
            mvc.perform(post("/orders").with(signedIn()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"addressId\":\"address-customer-1\",\"items\":[" + item + "]}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/orders").with(signedIn()).contentType(MediaType.APPLICATION_JSON).content("""
                {"addressId":"address-customer-1","items":[
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
    void gateCommitsApprovalSlotBeforeCallingToss() throws Exception {
        Checkout checkout = checkout(order());
        when(toss.confirm(any())).thenAnswer(invocation -> {
            ApprovalRequest sent = invocation.getArgument(0);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(attemptStatus(checkout.attemptId())).isEqualTo("APPROVING");
            assertThat(orderStatus(checkout.orderId())).isEqualTo("PAYMENT_IN_PROGRESS");
            assertThat(stock("tee-01", "M")).isEqualTo(19);
            assertThat(paymentCount()).isZero();
            assertThat(sent).isEqualTo(new ApprovalRequest(checkout.attemptId(), checkout.orderId(),
                    "payment-key", 19_000, "KRW"));
            return succeeded(19_000);
        });
        mvc.perform(post("/payments/confirm").with(signedIn()).contentType(MediaType.APPLICATION_JSON)
                        .content(confirmJson(checkout, "payment-key", 19_000)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.payment.status").value("SUCCEEDED"));
        assertThat(attemptStatus(checkout.attemptId())).isEqualTo("SUCCEEDED");
        assertThat(slot(checkout.orderId())).isEqualTo(checkout.attemptId());
        Payment payment = paymentRepository.findByOrderId(checkout.orderId()).orElseThrow();
        assertThat(payment.getAttemptId()).isEqualTo(checkout.attemptId());
        assertThat(payment.getPaymentKey()).isEqualTo("payment-key");
        assertThat(payment.getAmount()).isEqualTo(19_000);
        assertThat(payment.getApprovedAt()).isEqualTo(Instant.parse("2026-09-11T01:00:00Z"));
        assertThat(stock("tee-01", "M")).isEqualTo(19);
        verify(toss, never()).lookup(any());
    }

    @Test
    void paymentRecordRollsBackWithAttemptAndOrder() {
        Checkout checkout = checkout(order());
        when(toss.confirm(any())).thenReturn(succeeded(19_000));
        jdbc.execute("""
                CREATE FUNCTION reject_payment_record() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'payment storage unavailable'; END $$
                """);
        jdbc.execute("CREATE TRIGGER reject_payment_record BEFORE INSERT ON payments "
                + "FOR EACH ROW EXECUTE FUNCTION reject_payment_record()");
        try {
            assertThatThrownBy(() -> payments.confirm(request(checkout, "payment-key"), CUSTOMER))
                    .isInstanceOf(DataAccessException.class);
        } finally {
            jdbc.execute("DROP TRIGGER reject_payment_record ON payments");
            jdbc.execute("DROP FUNCTION reject_payment_record()");
        }
        assertThat(paymentCount()).isZero();
        assertThat(attemptStatus(checkout.attemptId())).isEqualTo("APPROVING");
        assertThat(orderStatus(checkout.orderId())).isEqualTo("PAYMENT_IN_PROGRESS");
    }

    @Test
    void wrongRequestAmountIsRejectedBeforeCallingToss() throws Exception {
        Checkout checkout = checkout(order());
        mvc.perform(post("/payments/confirm").with(signedIn()).contentType(MediaType.APPLICATION_JSON)
                        .content(confirmJson(checkout, "payment-key", 18_000)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AMOUNT_MISMATCH"));
        assertThat(approvalCount()).isZero();
        assertThat(attemptStatus(checkout.attemptId())).isEqualTo("STARTED");
        assertThat(orderStatus(checkout.orderId())).isEqualTo("PENDING_PAYMENT");
        verifyNoInteractions(toss);
    }

    @Test
    void confirmationRequiresAnAttempt() throws Exception {
        String orderId = order();
        mvc.perform(post("/payments/confirm").with(signedIn()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"" + orderId + "\",\"paymentKey\":\"payment-key\",\"amount\":19000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertThat(approvalCount()).isZero();
        verifyNoInteractions(toss);
    }

    @Test
    void anAttemptFromAnotherOrderCannotBeConfirmed() {
        String orderId = order();
        Checkout other = checkout(order());
        assertThatThrownBy(() -> payments.confirm(new ConfirmPaymentRequest(
                orderId, "payment-key", BigDecimal.valueOf(19_000), other.attemptId()), CUSTOMER))
                .isInstanceOf(ApiException.class);
        assertThat(approvalCount()).isZero();
        assertThat(attemptStatus(other.attemptId())).isEqualTo("STARTED");
        assertThat(orderStatus(orderId)).isEqualTo("PENDING_PAYMENT");
        verifyNoInteractions(toss);
    }

    @Test
    void gateRollsBackOrderSlotIfAttemptUpdateFails() {
        Checkout checkout = checkout(order());
        jdbc.execute("""
                CREATE FUNCTION reject_attempt_approving() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                  IF NEW.status = 'APPROVING' THEN RAISE EXCEPTION 'attempt storage unavailable'; END IF;
                  RETURN NEW;
                END $$
                """);
        jdbc.execute("CREATE TRIGGER reject_attempt_approving BEFORE UPDATE ON payment_attempts "
                + "FOR EACH ROW EXECUTE FUNCTION reject_attempt_approving()");
        try {
            assertThatThrownBy(() -> payments.confirm(request(checkout, "payment-key"), CUSTOMER))
                    .isInstanceOf(DataAccessException.class);
        } finally {
            jdbc.execute("DROP TRIGGER reject_attempt_approving ON payment_attempts");
            jdbc.execute("DROP FUNCTION reject_attempt_approving()");
        }
        assertThat(approvalCount()).isZero();
        assertThat(attemptStatus(checkout.attemptId())).isEqualTo("STARTED");
        assertThat(orderStatus(checkout.orderId())).isEqualTo("PENDING_PAYMENT");
        assertThat(slot(checkout.orderId())).isNull();
        assertThat(stock("tee-01", "M")).isEqualTo(20);
        verifyNoInteractions(toss);
    }

    @Test
    void itemsSoldOutAfterOrderingAreRejectedBeforeOpeningWindowAndAtApproval() throws Exception {
        Checkout checkout = checkout(order());
        setStock("tee-01", "M", 0);

        assertThatThrownBy(() -> attempts.start(checkout.orderId(), CUSTOMER))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("OUT_OF_STOCK");
        mvc.perform(post("/payments/confirm").with(signedIn()).contentType(MediaType.APPLICATION_JSON)
                        .content(confirmJson(checkout, "payment-key", 19_000)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OUT_OF_STOCK"));
        // 승인을 요청하지 않았으므로 시도는 인증 대기로 남고, 재고가 채워지면 같은 주문을 결제할 수 있다.
        assertThat(approvalCount()).isZero();
        assertThat(attemptStatus(checkout.attemptId())).isEqualTo("STARTED");
        assertThat(orderStatus(checkout.orderId())).isEqualTo("PENDING_PAYMENT");
        assertThat(slot(checkout.orderId())).isNull();
        assertThat(stock("tee-01", "M")).isZero();
        verifyNoInteractions(toss);
    }

    @Test
    void approvalTakesEveryLineOrNone() {
        String orderId = orders.create(cart(CUSTOMER_ADDRESS, new CreateOrderRequest.Item("tee-02", "L", 1),
                new CreateOrderRequest.Item("tee-01", "M", 2)), CUSTOMER).orderId();
        Checkout checkout = checkout(orderId);
        ConfirmPaymentRequest confirmation = new ConfirmPaymentRequest(orderId, "payment-key",
                BigDecimal.valueOf(66_000), checkout.attemptId());
        setStock("tee-02", "L", 0);

        assertThatThrownBy(() -> payments.confirm(confirmation, CUSTOMER))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("OUT_OF_STOCK");
        // tee-01 M을 먼저 뺐더라도 tee-02 L이 부족하면 함께 롤백된다.
        assertThat(stock("tee-01", "M")).isEqualTo(20);
        verifyNoInteractions(toss);

        setStock("tee-02", "L", 1);
        when(toss.confirm(any())).thenReturn(succeeded(66_000));
        assertThat(payments.confirm(confirmation, CUSTOMER).status().name()).isEqualTo("PAID");
        assertThat(stock("tee-01", "M")).isEqualTo(18);
        assertThat(stock("tee-02", "L")).isZero();
    }

    @Test
    void lastItemGoesToOnlyOneOfTwoConcurrentApprovals() throws Exception {
        setStock("tee-01", "M", 1);
        Checkout first = checkout(order());
        Checkout second = checkout(order());
        when(toss.confirm(any())).thenReturn(succeeded(19_000));
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var firstResult = executor.submit(() -> confirmOrErrorCode(first, start));
            var secondResult = executor.submit(() -> confirmOrErrorCode(second, start));
            start.countDown();
            assertThat(List.of(firstResult.get(10, TimeUnit.SECONDS), secondResult.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("PAID", "OUT_OF_STOCK");
        }
        assertThat(stock("tee-01", "M")).isZero();
        assertThat(approvalCount()).isEqualTo(1);
        assertThat(paymentCount()).isEqualTo(1);
        verify(toss).confirm(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"NOT_FOUND_PAYMENT", "NOT_FOUND_PAYMENT_SESSION"})
    void invalidPaymentKeyReleasesLastStockExactlyOnceForAnotherCustomer(String errorCode) {
        setStock("tee-01", "M", 1);
        Checkout invalid = checkout(order());
        when(toss.confirm(any())).thenReturn(PaymentResult.failed(errorCode));

        assertThat(payments.confirm(request(invalid, "invalid-key"), CUSTOMER).payment().status())
                .isEqualTo(PaymentState.FAILED);
        assertThat(stock("tee-01", "M")).isEqualTo(1);
        assertThat(slot(invalid.orderId())).isNull();
        // 같은 거절 결과로 다시 요청해도 재고를 두 번 돌려주지 않고, 새 승인도 보내지 않는다.
        payments.confirm(request(invalid, "invalid-key"), CUSTOMER);
        assertThat(stock("tee-01", "M")).isEqualTo(1);
        verify(toss).confirm(any());
        verify(toss, never()).lookup(any());

        String otherOrder = orders.create(cart(OTHER_CUSTOMER_ADDRESS, new CreateOrderRequest.Item("tee-01", "M", 1)),
                OTHER_CUSTOMER).orderId();
        String otherAttempt = attempts.start(otherOrder, OTHER_CUSTOMER).id();
        when(toss.confirm(any())).thenReturn(succeeded(19_000));
        assertThat(payments.confirm(new ConfirmPaymentRequest(otherOrder, "valid-key",
                BigDecimal.valueOf(19_000), otherAttempt), OTHER_CUSTOMER).payment().status())
                .isEqualTo(PaymentState.SUCCEEDED);
        assertThat(stock("tee-01", "M")).isZero();
        assertThat(paymentCount()).isEqualTo(1);
    }

    @Test
    void resultWriteFailureKeepsApprovalLiveUntilRecoveryConfirmsIt() {
        Checkout checkout = checkout(order());
        when(toss.confirm(any())).thenReturn(succeeded(19_000));
        jdbc.execute("""
                CREATE FUNCTION reject_order_paid() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                  IF NEW.status = 'PAID' THEN RAISE EXCEPTION 'order storage unavailable'; END IF;
                  RETURN NEW;
                END $$
                """);
        jdbc.execute("CREATE TRIGGER reject_order_paid BEFORE UPDATE ON purchase_orders "
                + "FOR EACH ROW EXECUTE FUNCTION reject_order_paid()");
        try {
            assertThatThrownBy(() -> payments.confirm(request(checkout, "payment-key"), CUSTOMER))
                    .isInstanceOf(DataAccessException.class);
        } finally {
            jdbc.execute("DROP TRIGGER reject_order_paid ON purchase_orders");
            jdbc.execute("DROP FUNCTION reject_order_paid()");
        }
        // PG는 승인했지만 DB에 반영하지 못했다. 슬롯이 남아 있어 새 결제는 막힌다.
        assertThat(attemptStatus(checkout.attemptId())).isEqualTo("APPROVING");
        assertThat(orderStatus(checkout.orderId())).isEqualTo("PAYMENT_IN_PROGRESS");
        assertThat(paymentCount()).isZero();
        assertThatThrownBy(() -> attempts.start(checkout.orderId(), CUSTOMER)).isInstanceOf(ApiException.class);
        verify(toss, never()).lookup(any());

        makeStale(checkout.attemptId());
        when(toss.lookup(any())).thenReturn(succeeded(19_000));
        assertThat(recovery.recoverUnresolved()).isEqualTo(1);
        assertThat(attemptStatus(checkout.attemptId())).isEqualTo("SUCCEEDED");
        assertThat(orderStatus(checkout.orderId())).isEqualTo("PAID");
        assertThat(paymentCount()).isEqualTo(1);
        verify(toss).confirm(any());
    }

    @Test
    void explicitDeclineReleasesOrderWithoutLookup() {
        Checkout checkout = checkout(order());
        when(toss.confirm(any())).thenReturn(PaymentResult.failed("REJECT_CARD_COMPANY"));
        assertThat(payments.confirm(request(checkout, "payment-key"), CUSTOMER).payment().status()).isEqualTo(PaymentState.FAILED);
        assertThat(attemptStatus(checkout.attemptId())).isEqualTo("FAILED");
        assertThat(orderStatus(checkout.orderId())).isEqualTo("PENDING_PAYMENT");
        assertThat(slot(checkout.orderId())).isNull();
        assertThat(paymentCount()).isZero();
        // 실패가 확인되면 승인 관문에서 가져간 재고를 돌려준다.
        assertThat(stock("tee-01", "M")).isEqualTo(20);
        verify(toss, never()).lookup(any());
    }

    @Test
    void uncertainApprovalIsCommittedBeforeOneLookupAndCanBecomeSuccessful() {
        Checkout checkout = checkout(order());
        when(toss.confirm(any())).thenReturn(PaymentResult.unknown("PG_COMMUNICATION_ERROR"));
        when(toss.lookup(any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(attemptStatus(checkout.attemptId())).isEqualTo("UNKNOWN");
            assertThat(orderStatus(checkout.orderId())).isEqualTo("PAYMENT_IN_PROGRESS");
            return succeeded(19_000);
        });
        assertThat(payments.confirm(request(checkout, "payment-key"), CUSTOMER).payment().status()).isEqualTo(PaymentState.SUCCEEDED);
        assertThat(attemptStatus(checkout.attemptId())).isEqualTo("SUCCEEDED");
        assertThat(orderStatus(checkout.orderId())).isEqualTo("PAID");
        assertThat(paymentCount()).isEqualTo(1);
        verify(toss).lookup(any());
    }

    @ParameterizedTest
    @MethodSource("reviewResults")
    void anomalousLookupRequiresReviewAndBlocksAnotherApproval(PaymentResult lookupResult) throws Exception {
        Checkout checkout = checkout(order());
        when(toss.confirm(any())).thenReturn(PaymentResult.unknown("PG_RESPONSE_MISMATCH"));
        when(toss.lookup(any())).thenReturn(lookupResult);
        mvc.perform(post("/payments/confirm").with(signedIn()).contentType(MediaType.APPLICATION_JSON)
                        .content(confirmJson(checkout, "payment-key", 19_000)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PAYMENT_IN_PROGRESS"))
                .andExpect(jsonPath("$.payment.status").value("REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.payment.errorCode").value(lookupResult.errorCode()));
        PaymentAttempt saved = attemptRepository.findByPaymentKey("payment-key").orElseThrow();
        assertThat(saved.getPgAmount()).isEqualByComparingTo(lookupResult.pgAmount());
        assertThat(saved.getPgCurrency()).isEqualTo(lookupResult.pgCurrency());
        assertThat(saved.getStatus()).isEqualTo(PaymentAttemptStatus.REVIEW_REQUIRED);
        assertThat(paymentCount()).isZero();
        assertThat(stock("tee-01", "M")).isEqualTo(19);
        assertThatThrownBy(() -> attempts.start(checkout.orderId(), CUSTOMER)).isInstanceOf(ApiException.class);
        verify(toss).confirm(any());
        verify(toss).lookup(any());
    }

    private static Stream<PaymentResult> reviewResults() {
        return Stream.of(
                new PaymentResult(Outcome.REVIEW_REQUIRED, "DONE", "PG_AMOUNT_MISMATCH",
                        Instant.parse("2026-09-11T01:00:00Z"), BigDecimal.valueOf(18_000), "KRW"),
                new PaymentResult(Outcome.REVIEW_REQUIRED, "DONE", "PG_CURRENCY_MISMATCH",
                        Instant.parse("2026-09-11T01:00:00Z"), BigDecimal.valueOf(19_000), "USD"),
                new PaymentResult(Outcome.REVIEW_REQUIRED, "DONE", "PG_UNSUPPORTED_METHOD",
                        Instant.parse("2026-09-11T01:00:00Z"), BigDecimal.valueOf(19_000), "KRW"),
                new PaymentResult(Outcome.REVIEW_REQUIRED, "CANCELED", "PG_UNEXPECTED_STATUS",
                        Instant.parse("2026-09-11T01:00:00Z"), BigDecimal.valueOf(19_000), "KRW"));
    }

    @Test
    void successEvidenceThatDoesNotMatchStoredAmountRequiresReview() {
        Checkout checkout = checkout(order());
        when(toss.confirm(any())).thenReturn(succeeded(18_000));
        assertThat(payments.confirm(request(checkout, "payment-key"), CUSTOMER).payment().status())
                .isEqualTo(PaymentState.REVIEW_REQUIRED);
        assertThat(orderStatus(checkout.orderId())).isEqualTo("PAYMENT_IN_PROGRESS");
        assertThat(jdbc.queryForObject("SELECT error_code FROM payment_attempts WHERE id = ?", String.class,
                checkout.attemptId())).isEqualTo("PG_EVIDENCE_MISMATCH");
        assertThat(paymentCount()).isZero();
    }

    @Test
    void unresolvedLookupStaysUnknownAndReadsDoNotCallToss() throws Exception {
        Checkout checkout = checkout(order());
        when(toss.confirm(any())).thenReturn(PaymentResult.unknown("PG_COMMUNICATION_ERROR"));
        when(toss.lookup(any())).thenReturn(PaymentResult.unknown("PG_LOOKUP_ERROR"));
        mvc.perform(post("/payments/confirm").with(signedIn()).contentType(MediaType.APPLICATION_JSON)
                        .content(confirmJson(checkout, "payment-key", 19_000)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PAYMENT_IN_PROGRESS"))
                .andExpect(jsonPath("$.payment.status").value("UNKNOWN"));
        mvc.perform(get("/orders/{id}", checkout.orderId()).with(signedIn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.status").value("UNKNOWN"));
        payments.confirm(request(checkout, "payment-key"), CUSTOMER);
        assertThatThrownBy(() -> attempts.start(checkout.orderId(), CUSTOMER)).isInstanceOf(ApiException.class);
        assertThat(paymentCount()).isZero();
        // 결과를 모르는 동안 재고를 붙잡아 두고, 같은 결제 키의 재요청은 다시 가져가지 않는다.
        assertThat(stock("tee-01", "M")).isEqualTo(19);
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
            var first = executor.submit(() -> payments.confirm(request(checkout, "payment-key"), CUSTOMER));
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                var duplicate = executor.submit(() -> payments.confirm(request(checkout, "payment-key"), CUSTOMER));
                assertThat(duplicate.get(3, TimeUnit.SECONDS).payment().status()).isEqualTo(PaymentState.APPROVING);
                var rejected = executor.submit(() -> assertThatThrownBy(
                        () -> payments.confirm(request(competing, "competing-key"), CUSTOMER)).isInstanceOf(ApiException.class));
                rejected.get(3, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
            assertThat(first.get(10, TimeUnit.SECONDS).payment().status()).isEqualTo(PaymentState.SUCCEEDED);
        }
        assertThat(approvalCount()).isEqualTo(1);
        assertThat(paymentCount()).isEqualTo(1);
        verify(toss).confirm(any());
    }

    @Test
    void closingWindowIsStoredWithoutApprovalAndOrderCanBeRetried() {
        Checkout first = checkout(order());
        attempts.authenticationResult(first.attemptId(), CUSTOMER,
                new PaymentAttemptService.AuthenticationResult(PaymentAttemptStatus.AUTH_CANCELED, "WINDOW_CLOSED"));
        assertThat(orders.get(first.orderId(), CUSTOMER).status().name()).isEqualTo("PENDING_PAYMENT");
        assertThat(orders.get(first.orderId(), CUSTOMER).latestAttempt().status()).isEqualTo(PaymentAttemptStatus.AUTH_CANCELED);
        assertThat(orders.get(first.orderId(), CUSTOMER).payment().status()).isEqualTo(PaymentState.READY);
        assertThat(approvalCount()).isZero();

        Checkout retry = checkout(first.orderId());
        when(toss.confirm(any())).thenReturn(succeeded(19_000));
        assertThat(payments.confirm(request(retry, "retry-key"), CUSTOMER).status().name()).isEqualTo("PAID");
        assertThat(jdbc.queryForList("SELECT status FROM payment_attempts WHERE order_id = ? ORDER BY started_at",
                String.class, first.orderId())).containsExactly("AUTH_CANCELED", "SUCCEEDED");
        assertThatThrownBy(() -> attempts.start(first.orderId(), CUSTOMER)).isInstanceOf(ApiException.class);
    }

    @Test
    void failedApprovalPreservesHistoryAndAllowsNewAttemptForSameOrder() {
        Checkout first = checkout(order());
        when(toss.confirm(any())).thenReturn(PaymentResult.failed("REJECT_CARD_COMPANY"), succeeded(19_000));
        assertThat(payments.confirm(request(first, "failed-key"), CUSTOMER).payment().status()).isEqualTo(PaymentState.FAILED);
        assertThat(orderStatus(first.orderId())).isEqualTo("PENDING_PAYMENT");
        assertThat(stock("tee-01", "M")).isEqualTo(20);

        Checkout retry = checkout(first.orderId());
        assertThat(payments.confirm(request(retry, "success-key"), CUSTOMER).payment().status()).isEqualTo(PaymentState.SUCCEEDED);
        assertThat(jdbc.queryForList("SELECT status FROM payment_attempts WHERE order_id = ? AND payment_key IS NOT NULL "
                + "ORDER BY approval_requested_at", String.class, first.orderId())).containsExactly("FAILED", "SUCCEEDED");
        assertThat(approvalCount()).isEqualTo(2);
        assertThat(stock("tee-01", "M")).isEqualTo(19);
        assertThat(slot(first.orderId())).isEqualTo(retry.attemptId());
        // 시도는 둘이지만 실제 결제 기록은 성공한 시도 하나뿐이다.
        assertThat(paymentCount()).isEqualTo(1);
        assertThat(paymentRepository.findByOrderId(first.orderId()).orElseThrow().getAttemptId())
                .isEqualTo(retry.attemptId());
        assertThatThrownBy(() -> attempts.start(first.orderId(), CUSTOMER)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> payments.confirm(request(first, "third-key"), CUSTOMER)).isInstanceOf(ApiException.class);
    }

    @Test
    void lateAuthenticationEventCannotCancelAnApproval() {
        Checkout checkout = checkout(order());
        when(toss.confirm(any())).thenReturn(succeeded(19_000));
        payments.confirm(request(checkout, "payment-key"), CUSTOMER);
        attempts.authenticationResult(checkout.attemptId(), CUSTOMER,
                new PaymentAttemptService.AuthenticationResult(PaymentAttemptStatus.AUTH_CANCELED, "WINDOW_CLOSED"));
        assertThat(attemptStatus(checkout.attemptId())).isEqualTo("SUCCEEDED");
        assertThat(orderStatus(checkout.orderId())).isEqualTo("PAID");
    }

    @Test
    void recoveryConfirmsApprovalLeftByServerStopWithoutApprovingAgain() {
        Checkout checkout = checkout(order());
        // 관문을 통과한 뒤 결과를 기록하기 전에 서버가 멈춘 상황이다.
        preparation.prepare(checkout.orderId(), CUSTOMER, "payment-key", checkout.attemptId(), BigDecimal.valueOf(19_000));
        makeStale(checkout.attemptId());
        when(toss.lookup(any())).thenReturn(succeeded(19_000));

        assertThat(recovery.recoverUnresolved()).isEqualTo(1);
        assertThat(attemptStatus(checkout.attemptId())).isEqualTo("SUCCEEDED");
        assertThat(orderStatus(checkout.orderId())).isEqualTo("PAID");
        assertThat(paymentCount()).isEqualTo(1);
        verify(toss, never()).confirm(any());
    }

    @Test
    void recoveryWaitsForPgToConfirmExpiryBeforeReleasingOrder() {
        Checkout checkout = checkout(order());
        preparation.prepare(checkout.orderId(), CUSTOMER, "payment-key", checkout.attemptId(), BigDecimal.valueOf(19_000));
        makeStale(checkout.attemptId());
        when(toss.lookup(any())).thenReturn(PaymentResult.unknown("PG_RESULT_UNCONFIRMED"),
                new PaymentResult(Outcome.FAILED, "EXPIRED", "PG_EXPIRED", null, BigDecimal.valueOf(19_000), "KRW"));

        recovery.recoverUnresolved();
        assertThat(attemptStatus(checkout.attemptId())).isEqualTo("UNKNOWN");
        assertThat(orderStatus(checkout.orderId())).isEqualTo("PAYMENT_IN_PROGRESS");
        assertThat(stock("tee-01", "M")).isEqualTo(19);
        assertThatThrownBy(() -> attempts.start(checkout.orderId(), CUSTOMER)).isInstanceOf(ApiException.class);

        makeStale(checkout.attemptId());
        recovery.recoverUnresolved();
        assertThat(attemptStatus(checkout.attemptId())).isEqualTo("FAILED");
        assertThat(orderStatus(checkout.orderId())).isEqualTo("PENDING_PAYMENT");
        assertThat(slot(checkout.orderId())).isNull();
        assertThat(stock("tee-01", "M")).isEqualTo(20);
        assertThat(checkout(checkout.orderId()).attemptId()).isNotEqualTo(checkout.attemptId());
        verify(toss, never()).confirm(any());
    }

    @Test
    void recoverySkipsApprovalsThatAreStillInProgress() {
        Checkout checkout = checkout(order());
        preparation.prepare(checkout.orderId(), CUSTOMER, "payment-key", checkout.attemptId(), BigDecimal.valueOf(19_000));
        assertThat(recovery.recoverUnresolved()).isZero();
        assertThat(attemptStatus(checkout.attemptId())).isEqualTo("APPROVING");
        verifyNoInteractions(toss);
    }

    @Test
    void approvalStillUnknownAfterDeadlineNeedsManualReview() {
        Checkout checkout = checkout(order());
        preparation.prepare(checkout.orderId(), CUSTOMER, "payment-key", checkout.attemptId(), BigDecimal.valueOf(19_000));
        jdbc.update("UPDATE payment_attempts SET approval_requested_at = now() - interval '2 hours' WHERE id = ?",
                checkout.attemptId());
        when(toss.lookup(any())).thenReturn(PaymentResult.unknown("PG_LOOKUP_ERROR"));

        assertThat(recovery.recoverUnresolved()).isEqualTo(1);
        assertThat(attemptStatus(checkout.attemptId())).isEqualTo("REVIEW_REQUIRED");
        assertThat(jdbc.queryForObject("SELECT error_code FROM payment_attempts WHERE id = ?", String.class,
                checkout.attemptId())).isEqualTo("PG_RESULT_TIMEOUT");
        assertThat(orderStatus(checkout.orderId())).isEqualTo("PAYMENT_IN_PROGRESS");
        assertThatThrownBy(() -> attempts.start(checkout.orderId(), CUSTOMER)).isInstanceOf(ApiException.class);

        // 수동 확인 대상은 자동 복구가 다시 조회하지 않는다.
        assertThat(stock("tee-01", "M")).isEqualTo(19);
        makeStale(checkout.attemptId());
        assertThat(recovery.recoverUnresolved()).isZero();
        verify(toss).lookup(any());
    }

    @Test
    void staleDuplicateRequestChecksPgInsteadOfApprovingAgain() throws Exception {
        Checkout checkout = checkout(order());
        when(toss.confirm(any())).thenReturn(PaymentResult.unknown("PG_COMMUNICATION_ERROR"));
        when(toss.lookup(any())).thenReturn(PaymentResult.unknown("PG_LOOKUP_ERROR"));
        payments.confirm(request(checkout, "payment-key"), CUSTOMER);
        makeStale(checkout.attemptId());
        when(toss.lookup(any())).thenReturn(succeeded(19_000));

        mvc.perform(post("/payments/confirm").with(signedIn()).contentType(MediaType.APPLICATION_JSON)
                        .content(confirmJson(checkout, "payment-key", 19_000)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.payment.status").value("SUCCEEDED"));
        verify(toss).confirm(any());
        verify(toss, times(2)).lookup(any());
    }

    @Test
    void databaseAllowsOnlyOneLiveApprovalPerOrder() {
        Checkout first = checkout(order());
        Checkout second = checkout(first.orderId());
        markApproving(first.attemptId(), "first-key");
        assertThatThrownBy(() -> markApproving(second.attemptId(), "second-key"))
                .isInstanceOf(DataAccessException.class);
        assertThat(approvalCount()).isEqualTo(1);
    }

    @Test
    void databaseAllowsOnlyOnePaymentPerOrder() {
        Checkout first = checkout(order());
        Checkout second = checkout(first.orderId());
        Checkout other = checkout(order());
        insertPayment(first.orderId(), first.attemptId(), "first-key");
        assertThatThrownBy(() -> insertPayment(second.orderId(), second.attemptId(), "second-key"))
                .isInstanceOf(DataAccessException.class);
        // 다른 주문의 시도로는 결제 기록을 만들 수 없다.
        assertThatThrownBy(() -> insertPayment(other.orderId(), second.attemptId(), "other-key"))
                .isInstanceOf(DataAccessException.class);
        assertThat(paymentCount()).isEqualTo(1);
    }

    @Test
    void databaseRejectsOrderSlotPointingToAnotherOrdersAttempt() {
        String orderId = order();
        Checkout other = checkout(order());
        assertThatThrownBy(() -> jdbc.update("UPDATE purchase_orders SET status = 'PAYMENT_IN_PROGRESS', "
                + "approval_attempt_id = ? WHERE id = ?", other.attemptId(), orderId))
                .isInstanceOf(DataAccessException.class);
        assertThat(slot(orderId)).isNull();
    }

    @Test
    void databaseKeepsOrderStatusAndSlotTogether() {
        Checkout checkout = checkout(order());
        for (String update : List.of(
                "status = 'PAYMENT_IN_PROGRESS'",
                "approval_attempt_id = '" + checkout.attemptId() + "'",
                "status = 'PAID', approval_attempt_id = '" + checkout.attemptId() + "'")) {
            assertThatThrownBy(() -> jdbc.update("UPDATE purchase_orders SET " + update + " WHERE id = ?",
                    checkout.orderId())).isInstanceOf(DataAccessException.class);
        }
        assertThat(orderStatus(checkout.orderId())).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void databaseRejectsApprovalStateWithoutPaymentKey() {
        Checkout checkout = checkout(order());
        assertThatThrownBy(() -> jdbc.update("UPDATE payment_attempts SET status = 'APPROVING' WHERE id = ?",
                checkout.attemptId())).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE payment_attempts SET payment_key = 'key', "
                + "approval_requested_at = now() WHERE id = ?", checkout.attemptId()))
                .isInstanceOf(DataAccessException.class);
        assertThat(attemptStatus(checkout.attemptId())).isEqualTo("STARTED");
    }

    @Test
    void databaseKeepsFinishTimeOnlyOnFinalAttempts() {
        Checkout checkout = checkout(order());
        assertThatThrownBy(() -> jdbc.update("UPDATE payment_attempts SET finished_at = now() WHERE id = ?",
                checkout.attemptId())).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE payment_attempts SET status = 'AUTH_CANCELED' WHERE id = ?",
                checkout.attemptId())).isInstanceOf(DataAccessException.class);
        markApproving(checkout.attemptId(), "payment-key");
        assertThatThrownBy(() -> jdbc.update("UPDATE payment_attempts SET status = 'REVIEW_REQUIRED', "
                + "finished_at = now() WHERE id = ?", checkout.attemptId())).isInstanceOf(DataAccessException.class);
        assertThat(attemptStatus(checkout.attemptId())).isEqualTo("APPROVING");
    }

    @Test
    void databaseRejectsSuccessWithoutMatchingApprovalEvidence() {
        Checkout checkout = checkout(order());
        markApproving(checkout.attemptId(), "payment-key");
        for (String evidence : List.of(
                "pg_status = 'DONE', pg_amount = 18000, pg_currency = 'KRW'",
                "pg_status = 'DONE', pg_amount = 19000, pg_currency = 'USD'",
                "pg_status = 'DONE', pg_amount = NULL, pg_currency = 'KRW'",
                "pg_status = 'DONE', pg_amount = 19000, pg_currency = NULL",
                "pg_status = NULL, pg_amount = 19000, pg_currency = 'KRW'")) {
            assertThatThrownBy(() -> jdbc.update("UPDATE payment_attempts SET status = 'SUCCEEDED', "
                    + "finished_at = now(), pg_approved_at = now(), last_checked_at = now(), " + evidence
                    + " WHERE id = ?", checkout.attemptId())).isInstanceOf(DataAccessException.class);
        }
        assertThat(attemptStatus(checkout.attemptId())).isEqualTo("APPROVING");
    }

    private static CreateOrderRequest cart(String addressId, CreateOrderRequest.Item... items) {
        return new CreateOrderRequest(List.of(items), addressId, null);
    }

    private void saveAddress(String addressId, String customerId) {
        jdbc.update("""
                INSERT INTO customer_addresses (id, customer_id, label, recipient_name, phone, postal_code, address,
                    address_detail, is_default, created_at, updated_at)
                VALUES (?, ?, '집', '김모도', '01012345678', '06236', '서울 강남구 테헤란로 123', '101호', true, now(), now())
                """, addressId, customerId);
    }

    private String order() {
        return orders.create(cart(CUSTOMER_ADDRESS, new CreateOrderRequest.Item("tee-01", "M", 1)), CUSTOMER).orderId();
    }

    private Checkout checkout(String orderId) {
        return new Checkout(orderId, attempts.start(orderId, CUSTOMER).id());
    }

    private static RequestPostProcessor signedIn() {
        return signedIn(CUSTOMER);
    }

    private static RequestPostProcessor signedIn(String customerId) {
        return jwt().jwt(token -> token.subject(customerId));
    }

    private static Jwt keycloakToken(String value, String... realmRoles) {
        return Jwt.withTokenValue(value).header("alg", "RS256").subject(value)
                .claim("realm_access", Map.of("roles", List.of(realmRoles))).build();
    }

    private List<String> columns(String table) {
        return jdbc.queryForList("SELECT column_name FROM information_schema.columns "
                + "WHERE table_schema = 'public' AND table_name = ?", String.class, table);
    }

    private String orderStatus(String orderId) {
        return jdbc.queryForObject("SELECT status FROM purchase_orders WHERE id = ?", String.class, orderId);
    }

    private String slot(String orderId) {
        return jdbc.queryForObject("SELECT approval_attempt_id FROM purchase_orders WHERE id = ?", String.class, orderId);
    }

    private String attemptStatus(String attemptId) {
        return jdbc.queryForObject("SELECT status FROM payment_attempts WHERE id = ?", String.class, attemptId);
    }

    private long approvalCount() {
        return jdbc.queryForObject("SELECT count(*) FROM payment_attempts WHERE payment_key IS NOT NULL", Long.class);
    }

    private long paymentCount() {
        return jdbc.queryForObject("SELECT count(*) FROM payments", Long.class);
    }

    private int stock(String productId, String size) {
        return jdbc.queryForObject("SELECT quantity FROM product_stocks WHERE product_id = ? AND size = ?",
                Integer.class, productId, size);
    }

    private void setStock(String productId, String size, int quantity) {
        jdbc.update("UPDATE product_stocks SET quantity = ? WHERE product_id = ? AND size = ?", quantity, productId, size);
    }

    private String confirmOrErrorCode(Checkout checkout, CountDownLatch start) throws InterruptedException {
        start.await();
        try {
            return payments.confirm(request(checkout, "key-" + checkout.attemptId()), CUSTOMER).status().name();
        } catch (ApiException exception) {
            return exception.code();
        }
    }

    private void insertPayment(String orderId, String attemptId, String paymentKey) {
        jdbc.update("""
                INSERT INTO payments (id, order_id, attempt_id, payment_key, amount, currency, approved_at, created_at)
                VALUES (gen_random_uuid()::text, ?, ?, ?, 19000, 'KRW', now(), now())
                """, orderId, attemptId, paymentKey);
    }

    private void markApproving(String attemptId, String paymentKey) {
        jdbc.update("UPDATE payment_attempts SET status = 'APPROVING', payment_key = ?, approval_requested_at = now() "
                + "WHERE id = ?", paymentKey, attemptId);
    }

    // 복구 작업은 마지막 확인 뒤 1분이 지난 시도만 조회하므로 시각을 과거로 옮긴다.
    private void makeStale(String attemptId) {
        jdbc.update("UPDATE payment_attempts SET approval_requested_at = now() - interval '2 minutes', "
                + "last_checked_at = CASE WHEN last_checked_at IS NULL THEN NULL ELSE now() - interval '2 minutes' END "
                + "WHERE id = ?", attemptId);
    }

    private static PaymentResult succeeded(long amount) {
        return new PaymentResult(Outcome.SUCCEEDED, "DONE", null,
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

    // 관리자 API가 아직 없으므로 /admin/** 접근 규칙을 확인할 테스트 전용 엔드포인트를 둔다.
    @RestController
    static class AdminProbe {
        @GetMapping("/admin/probe")
        String probe() {
            return "ok";
        }
    }
}
