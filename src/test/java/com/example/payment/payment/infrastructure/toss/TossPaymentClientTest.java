package com.example.payment.payment.infrastructure.toss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.payment.payment.domain.ApprovalRequest;
import com.example.payment.payment.domain.PaymentResult;
import com.example.payment.payment.domain.PaymentResult.Outcome;
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

class TossPaymentClientTest {
    private static final String BASE = "https://api.tosspayments.com/v1/payments";
    private static final ApprovalRequest PAYMENT = new ApprovalRequest("attempt-123", "order-123", "payment-key", 10000, "KRW");
    private static final String DONE = """
            {"orderId":"order-123","paymentKey":"payment-key","totalAmount":10000,"currency":"KRW",
             "status":"DONE","method":"카드","approvedAt":"2026-09-11T10:00:00+09:00","futureField":"ignored"}
            """;
    private MockRestServiceServer server;
    private TossPaymentClient gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        new TossPaymentConfiguration().tossRestClient(builder,
                new TossProperties("test_gck_gateway", "test_gsk_gateway", null, null));
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new TossPaymentClient(builder.build());
    }

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    @Test
    void sendsPersistedAmountAndAttemptIdWithBasicAuth() {
        server.expect(requestTo(BASE + "/confirm")).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Basic " + Base64.getEncoder()
                        .encodeToString("test_gsk_gateway:".getBytes(StandardCharsets.UTF_8))))
                .andExpect(header("Idempotency-Key", "attempt-123"))
                .andExpect(content().json("""
                        {"orderId":"order-123","paymentKey":"payment-key","amount":15000}
                        """))
                .andRespond(withSuccess(DONE.replace("10000", "15000"), MediaType.APPLICATION_JSON));
        ApprovalRequest payment = new ApprovalRequest("attempt-123", "order-123", "payment-key", 15000, "KRW");
        PaymentResult result = gateway.confirm(payment);
        assertThat(result.outcome()).isEqualTo(Outcome.SUCCEEDED);
        assertThat(result.pgAmount()).isEqualByComparingTo("15000");
        assertThat(result.approvedAt()).hasToString("2026-09-11T01:00:00Z");
    }

    @ParameterizedTest
    @CsvSource({"order-123,another-order", "payment-key,another-key", "10000,10001", "10000,10000.5",
            "KRW,USD", "DONE,IN_PROGRESS", "DONE,READY", "DONE,CANCELED", "DONE,PARTIAL_CANCELED", "카드,계좌이체"})
    void neverMarksMismatchedOrUnapprovedResponseAsPaid(String from, String to) {
        server.expect(requestTo(BASE + "/confirm"))
                .andRespond(withSuccess(DONE.replace(from, to), MediaType.APPLICATION_JSON));
        assertThat(gateway.confirm(PAYMENT).outcome()).isEqualTo(Outcome.UNKNOWN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "{invalid-json}", ""})
    void missingOrMalformedResponsesStayUnknown(String response) {
        server.expect(requestTo(BASE + "/confirm")).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        assertThat(gateway.confirm(PAYMENT).outcome()).isEqualTo(Outcome.UNKNOWN);
    }

    @Test
    void doneWithoutApprovalTimeIsUnknown() {
        server.expect(requestTo(BASE + "/confirm")).andRespond(withSuccess(
                DONE.replace("\"2026-09-11T10:00:00+09:00\"", "null"), MediaType.APPLICATION_JSON));
        assertThat(gateway.confirm(PAYMENT).outcome()).isEqualTo(Outcome.UNKNOWN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ABORTED", "EXPIRED"})
    void authoritativeTerminalFailureIsFailed(String status) {
        server.expect(requestTo(BASE + "/payment-key")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(DONE.replace("DONE", status), MediaType.APPLICATION_JSON));
        assertThat(gateway.lookup(PAYMENT).outcome()).isEqualTo(Outcome.FAILED);
    }

    @ParameterizedTest
    @CsvSource({"403,REJECT_CARD_COMPANY", "400,INVALID_REJECT_CARD", "400,INVALID_STOPPED_CARD"})
    void explicitCardDeclineIsFailed(int status, String code) {
        server.expect(requestTo(BASE + "/confirm")).andRespond(withStatus(HttpStatus.valueOf(status))
                .contentType(MediaType.APPLICATION_JSON).body("{\"code\":\"" + code + "\"}"));
        assertThat(gateway.confirm(PAYMENT)).isEqualTo(PaymentResult.failed(code));
    }

    @ParameterizedTest
    @CsvSource({"400,ALREADY_PROCESSED_PAYMENT", "409,IDEMPOTENT_REQUEST_PROCESSING", "401,UNAUTHORIZED_KEY",
            "500,REJECT_CARD_COMPANY", "429,TOO_MANY_REQUESTS", "400,UNKNOWN_NEW_ERROR", "404,NOT_FOUND_PAYMENT_SESSION"})
    void ambiguousErrorsStayUnknown(int status, String code) {
        server.expect(requestTo(BASE + "/confirm")).andRespond(withStatus(HttpStatus.valueOf(status))
                .contentType(MediaType.APPLICATION_JSON).body("{\"code\":\"" + code + "\"}"));
        assertThat(gateway.confirm(PAYMENT)).isEqualTo(PaymentResult.unknown(code));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{\"amount\":5000}"})
    void easyPayUsesTotalAmountRatherThanCardPortion(String card) {
        String response = DONE.replace("카드", "간편결제")
                .replace("\"futureField\":\"ignored\"", "\"card\":" + card);
        server.expect(requestTo(BASE + "/confirm")).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        assertThat(gateway.confirm(PAYMENT).outcome()).isEqualTo(Outcome.SUCCEEDED);
    }

    @Test
    void timeoutDoesNotMeanDeclined() {
        server.expect(requestTo(BASE + "/confirm"))
                .andRespond(withException(new SocketTimeoutException("response lost")));
        assertThat(gateway.confirm(PAYMENT)).isEqualTo(PaymentResult.unknown("PG_COMMUNICATION_ERROR"));
    }

    @Test
    void lookup404DoesNotMeanUnpaid() {
        server.expect(requestTo(BASE + "/payment-key")).andRespond(withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON).body("{\"code\":\"NOT_FOUND_PAYMENT\"}"));
        assertThat(gateway.lookup(PAYMENT)).isEqualTo(PaymentResult.unknown("PG_LOOKUP_ERROR"));
    }

    @Test
    void malformedErrorBodyStaysUnknown() {
        server.expect(requestTo(BASE + "/confirm")).andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                .contentType(MediaType.TEXT_HTML).body("<html>gateway unavailable</html>"));
        assertThat(gateway.confirm(PAYMENT).outcome()).isEqualTo(Outcome.UNKNOWN);
    }

    @Test
    void lookupRestoresSuccessfulPayment() {
        server.expect(requestTo(BASE + "/payment-key")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(DONE, MediaType.APPLICATION_JSON));
        assertThat(gateway.lookup(PAYMENT).outcome()).isEqualTo(Outcome.SUCCEEDED);
    }

    @ParameterizedTest
    @CsvSource({"10000,9000", "KRW,USD"})
    void persistentAmountOrCurrencyMismatchRequiresReview(String from, String to) {
        String response = DONE.replace(from, to);
        server.expect(requestTo(BASE + "/confirm")).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/payment-key")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        assertThat(gateway.confirm(PAYMENT).outcome()).isEqualTo(Outcome.UNKNOWN);
        PaymentResult result = gateway.lookup(PAYMENT);
        assertThat(result.outcome()).isEqualTo(Outcome.REVIEW_REQUIRED);
        assertThat(result.errorCode()).isEqualTo("PG_AMOUNT_MISMATCH");
        assertThat(result.pgAmount()).isEqualByComparingTo(from.equals("10000") ? "9000" : "10000");
        assertThat(result.pgCurrency()).isEqualTo(from.equals("KRW") ? "USD" : "KRW");
    }

    @ParameterizedTest
    @CsvSource({"order-123,other-order", "payment-key,other-key"})
    void identityMismatchRequiresReview(String from, String to) {
        server.expect(requestTo(BASE + "/payment-key"))
                .andRespond(withSuccess(DONE.replace(from, to), MediaType.APPLICATION_JSON));
        PaymentResult result = gateway.lookup(PAYMENT);
        assertThat(result).isEqualTo(PaymentResult.reviewRequired("PG_IDENTITY_MISMATCH"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CANCELED", "PARTIAL_CANCELED"})
    void externallyCanceledPaymentRequiresReview(String status) {
        server.expect(requestTo(BASE + "/payment-key"))
                .andRespond(withSuccess(DONE.replace("DONE", status), MediaType.APPLICATION_JSON));
        PaymentResult result = gateway.lookup(PAYMENT);
        assertThat(result.outcome()).isEqualTo(Outcome.REVIEW_REQUIRED);
        assertThat(result.pgStatus()).isEqualTo(status);
    }

    @Test
    void unsupportedMethodRequiresReview() {
        server.expect(requestTo(BASE + "/payment-key"))
                .andRespond(withSuccess(DONE.replace("카드", "계좌이체"), MediaType.APPLICATION_JSON));
        assertThat(gateway.lookup(PAYMENT).errorCode()).isEqualTo("PG_UNSUPPORTED_METHOD");
    }
}
