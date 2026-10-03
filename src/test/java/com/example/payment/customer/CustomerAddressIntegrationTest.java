package com.example.payment.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.payment.config.SecurityConfiguration;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {"payment.recovery.enabled=false", "order.unpaid-expiry.enabled=false"})
@AutoConfigureMockMvc
@Testcontainers
class CustomerAddressIntegrationTest {
    private static final String BUYER = "buyer-1";
    private static final String OTHER = "buyer-2";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired CustomerAddressService addresses;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE TABLE customer_addresses");
    }

    @Test
    void firstAddressBecomesDefaultAndIsListedFirst() throws Exception {
        create(BUYER, "집", false)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/me/addresses/")))
                .andExpect(jsonPath("$.defaultAddress").value(true))
                .andExpect(jsonPath("$.recipientName").value("홍길동"))
                .andExpect(jsonPath("$.phone").value("01012345678"))
                .andExpect(jsonPath("$.addressDetail").value("101호"));
        create(BUYER, "회사", false)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.defaultAddress").value(false));

        mvc.perform(get("/me/addresses").with(customer(BUYER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].label").value("집"))
                .andExpect(jsonPath("$[1].label").value("회사"));
    }

    @Test
    void choosingAnotherDefaultKeepsExactlyOneDefault() throws Exception {
        String home = id(create(BUYER, "집", false));
        String office = id(create(BUYER, "회사", true));
        assertThat(defaults(BUYER)).containsExactly(office);

        mvc.perform(put("/me/addresses/{id}/default", home).with(customer(BUYER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultAddress").value(true));
        assertThat(defaults(BUYER)).containsExactly(home);

        mvc.perform(put("/me/addresses/{id}", office).with(customer(BUYER))
                        .contentType(MediaType.APPLICATION_JSON).content(body("회사", "202호", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addressDetail").value("202호"))
                .andExpect(jsonPath("$.defaultAddress").value(true));
        assertThat(defaults(BUYER)).containsExactly(office);

        // 기본 배송지를 고치면서 makeDefault를 빼도 기본 배송지는 그대로다.
        mvc.perform(put("/me/addresses/{id}", office).with(customer(BUYER))
                        .contentType(MediaType.APPLICATION_JSON).content(body("회사", "303호", false)))
                .andExpect(jsonPath("$.defaultAddress").value(true));
        assertThat(defaults(BUYER)).containsExactly(office);
    }

    @Test
    void deletingTheDefaultPromotesTheMostRecentRemainingAddress() throws Exception {
        String first = id(create(BUYER, "첫째", false));
        String second = id(create(BUYER, "둘째", false));
        String third = id(create(BUYER, "셋째", false));

        mvc.perform(delete("/me/addresses/{id}", first).with(customer(BUYER))).andExpect(status().isNoContent());
        assertThat(defaults(BUYER)).containsExactly(third);
        mvc.perform(delete("/me/addresses/{id}", second).with(customer(BUYER))).andExpect(status().isNoContent());
        assertThat(defaults(BUYER)).containsExactly(third);
        mvc.perform(delete("/me/addresses/{id}", third).with(customer(BUYER))).andExpect(status().isNoContent());
        assertThat(count(BUYER)).isZero();

        mvc.perform(delete("/me/addresses/{id}", third).with(customer(BUYER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ADDRESS_NOT_FOUND"));
    }

    @Test
    void anotherCustomersAddressLooksMissing() throws Exception {
        String home = id(create(BUYER, "집", false));

        mvc.perform(get("/me/addresses").with(customer(OTHER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(put("/me/addresses/{id}", home).with(customer(OTHER))
                        .contentType(MediaType.APPLICATION_JSON).content(body("가로채기", "", false)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ADDRESS_NOT_FOUND"));
        mvc.perform(put("/me/addresses/{id}/default", home).with(customer(OTHER))).andExpect(status().isNotFound());
        mvc.perform(delete("/me/addresses/{id}", home).with(customer(OTHER))).andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("SELECT label FROM customer_addresses WHERE id = ?", String.class, home))
                .isEqualTo("집");
        assertThat(defaults(BUYER)).containsExactly(home);
    }

    @Test
    void aCustomerCanSaveUpToTenAddresses() throws Exception {
        for (int i = 1; i <= CustomerAddressService.MAX_ADDRESSES; i++) {
            create(BUYER, "배송지" + i, false).andExpect(status().isCreated());
        }
        create(BUYER, "열한번째", false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADDRESS_LIMIT_EXCEEDED"));
        assertThat(count(BUYER)).isEqualTo(CustomerAddressService.MAX_ADDRESSES);
        // 다른 회원의 개수와는 상관없다.
        create(OTHER, "집", false).andExpect(status().isCreated());
    }

    @Test
    void invalidAddressesAreRejected() throws Exception {
        for (String invalid : List.of(
                body("집", "", false).replace("010-1234-5678", "02-12"),
                body("집", "", false).replace("010-1234-5678", "1012345678"),
                body("집", "", false).replace("06236", "1234"),
                body(" ", "", false),
                body("집", "가".repeat(101), false),
                "{\"label\":\"집\"}")) {
            mvc.perform(post("/me/addresses").with(customer(BUYER))
                            .contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        assertThat(count(BUYER)).isZero();
    }

    @Test
    void concurrentFirstAddressesStillLeaveOneDefault() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        AddressRequest request = new AddressRequest("집", "홍길동", "010-1234-5678", "06236",
                "서울 강남구 테헤란로 123", "", false);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> { start.await(); return addresses.create(BUYER, request); });
            var second = executor.submit(() -> { start.await(); return addresses.create(BUYER, request); });
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }
        assertThat(count(BUYER)).isEqualTo(2);
        assertThat(defaults(BUYER)).hasSize(1);
    }

    @Test
    void databaseAllowsOnlyOneDefaultPerCustomer() throws Exception {
        create(BUYER, "집", false);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO customer_addresses (id, customer_id, label, recipient_name, phone, postal_code, address,
                    address_detail, is_default, created_at, updated_at)
                VALUES (gen_random_uuid()::text, ?, '회사', '홍길동', '01012345678', '06236', '서울', '', true, now(), now())
                """, BUYER)).isInstanceOf(DataAccessException.class);
        assertThat(defaults(BUYER)).hasSize(1);
    }

    @Test
    void shopAdminsDoNotHaveAMyPage() throws Exception {
        mvc.perform(get("/me/addresses").with(admin()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(post("/me/addresses").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(body("집", "", false)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/me/orders").with(admin())).andExpect(status().isForbidden());
        mvc.perform(get("/me/addresses")).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_addresses", Long.class)).isZero();
    }

    private ResultActions create(String customerId, String label, boolean makeDefault) throws Exception {
        return mvc.perform(post("/me/addresses").with(customer(customerId))
                .contentType(MediaType.APPLICATION_JSON).content(body(label, "101호", makeDefault)));
    }

    private static String body(String label, String detail, boolean makeDefault) {
        return """
                {"label":"%s","recipientName":" 홍길동 ","phone":"010-1234-5678","postalCode":"06236",
                 "address":"서울 강남구 테헤란로 123","addressDetail":"%s","makeDefault":%s}
                """.formatted(label, detail, makeDefault);
    }

    private static String id(ResultActions created) throws Exception {
        return JsonPath.read(created.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
    }

    private List<String> defaults(String customerId) {
        return jdbc.queryForList("SELECT id FROM customer_addresses WHERE customer_id = ? AND is_default",
                String.class, customerId);
    }

    private long count(String customerId) {
        return jdbc.queryForObject("SELECT count(*) FROM customer_addresses WHERE customer_id = ?", Long.class,
                customerId);
    }

    private static RequestPostProcessor customer(String customerId) {
        return jwt().jwt(token -> token.subject(customerId));
    }

    private static RequestPostProcessor admin() {
        return jwt().jwt(token -> token.subject("admin-1"))
                .authorities(new SimpleGrantedAuthority("ROLE_" + SecurityConfiguration.SHOP_ADMIN));
    }
}
