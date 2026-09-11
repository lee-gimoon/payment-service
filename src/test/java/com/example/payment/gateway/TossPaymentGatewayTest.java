package com.example.payment.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.payment.config.PaymentConfiguration;
import com.example.payment.config.TossProperties;
import com.example.payment.gateway.PaymentGateway.PaymentCommand;
import com.example.payment.payment.PaymentOutcome;
import com.example.payment.payment.PaymentStatus;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TossPaymentGatewayTest {
    private static final String BASE = "https://api.tosspayments.com/v1/payments";
    private static final PaymentCommand COMMAND = new PaymentCommand("order-123", "payment-key", 10_000, "attempt-123", "operation-123");
    private static final String DONE = """
            {"orderId":"order-123","paymentKey":"payment-key","totalAmount":10000,"currency":"KRW",
             "status":"DONE","method":"카드","approvedAt":"2026-09-11T10:00:00+09:00","futureField":"ignored"}
            """;
    private MockRestServiceServer server;
    private TossPaymentGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        new PaymentConfiguration().tossRestClient(builder, new TossProperties("test_ck_gateway", "test_sk_gateway"));
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new TossPaymentGateway(builder.build());
    }

    @AfterEach
    void verifyRequests() { server.verify(); }

    @Test
    void sendsExactContractAndValidatesApproval() {
        server.expect(requestTo(BASE + "/confirm")).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Basic " + Base64.getEncoder().encodeToString("test_sk_gateway:".getBytes(StandardCharsets.UTF_8))))
                .andExpect(header("Idempotency-Key", "attempt-123"))
                .andExpect(content().json(""" 
                        {"orderId":"order-123","paymentKey":"payment-key","amount":10000}
                        """))
                .andRespond(withSuccess(DONE, MediaType.APPLICATION_JSON));
        PaymentOutcome outcome = gateway.confirm(COMMAND);
        assertThat(outcome.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(outcome.approvedAt()).hasToString("2026-09-11T01:00:00Z");
    }

    @ParameterizedTest
    @CsvSource({"order-123,another-order", "payment-key,another-key", "10000,10001", "10000,10000.5", "KRW,USD", "DONE,IN_PROGRESS", "DONE,READY", "DONE,CANCELED", "DONE,PARTIAL_CANCELED", "카드,계좌이체"})
    void neverMarksMismatchedOrUnapprovedResponseAsPaid(String from, String to) {
        server.expect(requestTo(BASE + "/confirm")).andRespond(withSuccess(DONE.replace(from, to), MediaType.APPLICATION_JSON));
        assertThat(gateway.confirm(COMMAND).status()).isEqualTo(PaymentStatus.UNKNOWN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "{invalid-json}", ""})
    void missingOrMalformedResponsesStayUnknown(String response) {
        server.expect(requestTo(BASE + "/confirm")).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        assertThat(gateway.confirm(COMMAND).status()).isEqualTo(PaymentStatus.UNKNOWN);
    }

    @Test
    void doneWithoutApprovalTimeIsUnknown() {
        server.expect(requestTo(BASE + "/confirm")).andRespond(withSuccess(DONE.replace("\"2026-09-11T10:00:00+09:00\"", "null"), MediaType.APPLICATION_JSON));
        assertThat(gateway.confirm(COMMAND).status()).isEqualTo(PaymentStatus.UNKNOWN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ABORTED", "EXPIRED"})
    void authoritativeTerminalFailureIsFailed(String status) {
        server.expect(requestTo(BASE + "/payment-key")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(DONE.replace("DONE", status), MediaType.APPLICATION_JSON));
        assertThat(gateway.lookup(COMMAND).status()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void explicitCardDeclineIsFailed() {
        server.expect(requestTo(BASE + "/confirm")).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON).body("{\"code\":\"REJECT_CARD_COMPANY\"}"));
        assertThat(gateway.confirm(COMMAND)).isEqualTo(PaymentOutcome.failed("REJECT_CARD_COMPANY"));
    }

    @ParameterizedTest
    @CsvSource({"400,ALREADY_PROCESSED_PAYMENT", "409,IDEMPOTENT_REQUEST_PROCESSING", "401,UNAUTHORIZED_KEY", "500,REJECT_CARD_COMPANY", "429,TOO_MANY_REQUESTS", "400,UNKNOWN_NEW_ERROR"})
    void ambiguousErrorsStayUnknown(int status, String code) {
        server.expect(requestTo(BASE + "/confirm")).andRespond(withStatus(HttpStatus.valueOf(status))
                .contentType(MediaType.APPLICATION_JSON).body("{\"code\":\"" + code + "\"}"));
        assertThat(gateway.confirm(COMMAND).status()).isEqualTo(PaymentStatus.UNKNOWN);
    }

    @Test
    void timeoutDoesNotMeanDeclined() {
        server.expect(requestTo(BASE + "/confirm")).andRespond(withException(new SocketTimeoutException("response lost")));
        assertThat(gateway.confirm(COMMAND)).isEqualTo(PaymentOutcome.unknown("PG_COMMUNICATION_ERROR"));
    }

    @Test
    void lookup404DoesNotMeanUnpaid() {
        server.expect(requestTo(BASE + "/payment-key")).andRespond(withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON).body("{\"code\":\"NOT_FOUND_PAYMENT\"}"));
        assertThat(gateway.lookup(COMMAND).status()).isEqualTo(PaymentStatus.UNKNOWN);
    }

    @Test
    void lookupRestoresSuccessfulPayment() {
        server.expect(requestTo(BASE + "/payment-key")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(DONE, MediaType.APPLICATION_JSON));
        assertThat(gateway.lookup(COMMAND).status()).isEqualTo(PaymentStatus.SUCCEEDED);
    }
}
