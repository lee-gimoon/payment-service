/* 파일 역할: 실제 토스 API 대신 모의 HTTP 응답으로 PG 요청 계약과 결제 결과 해석을 검증한다. */
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
import com.example.payment.payment.Payment;
import com.example.payment.payment.PaymentResult;
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

/** 토스 전용 클라이언트의 요청 헤더·본문과 성공·거절·미확정 응답 처리를 검증한다. */
class TossPaymentClientTest {
    private static final String BASE = "https://api.tosspayments.com/v1/payments";
    private static final Payment PAYMENT = new Payment("order-123", "payment-key");
    private static final String DONE = """
            {"orderId":"order-123","paymentKey":"payment-key","totalAmount":10000,"currency":"KRW",
             "status":"DONE","method":"카드","approvedAt":"2026-09-11T10:00:00+09:00","futureField":"ignored"}
            """;
    private MockRestServiceServer server;
    private TossPaymentClient gateway;

    /** 각 테스트마다 실제 설정을 적용한 RestClient에 모의 응답 서버를 연결한다. */
    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        new PaymentConfiguration().tossRestClient(builder, new TossProperties("test_gck_gateway", "test_gsk_gateway", null, null));
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new TossPaymentClient(builder.build());
    }

    /** 테스트에서 기대한 HTTP 요청들이 실제로 모두 발생했는지 확인한다. */
    @AfterEach
    void verifyRequests() { server.verify(); }

    /** 승인 URL·POST·인증·멱등 키·JSON 본문과 성공 결과의 UTC 승인 시각을 확인한다. */
    @Test
    void sendsExactContractAndValidatesApproval() {
        server.expect(requestTo(BASE + "/confirm")).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Basic " + Base64.getEncoder().encodeToString("test_gsk_gateway:".getBytes(StandardCharsets.UTF_8))))
                .andExpect(header("Idempotency-Key", "order-123"))
                .andExpect(content().json(""" 
                        {"orderId":"order-123","paymentKey":"payment-key","amount":10000}
                        """))
                .andRespond(withSuccess(DONE, MediaType.APPLICATION_JSON));
        PaymentResult outcome = gateway.confirm(PAYMENT, 10_000);
        assertThat(outcome.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(outcome.approvedAt()).hasToString("2026-09-11T01:00:00Z");
    }

    /** 결제 정보 불일치, 미승인 상태, 미지원 결제 수단을 성공으로 처리하지 않는지 확인한다. */
    @ParameterizedTest
    @CsvSource({"order-123,another-order", "payment-key,another-key", "10000,10001", "10000,10000.5", "KRW,USD", "DONE,IN_PROGRESS", "DONE,READY", "DONE,CANCELED", "DONE,PARTIAL_CANCELED", "카드,계좌이체"})
    void neverMarksMismatchedOrUnapprovedResponseAsPaid(String from, String to) {
        server.expect(requestTo(BASE + "/confirm")).andRespond(withSuccess(DONE.replace(from, to), MediaType.APPLICATION_JSON));
        assertThat(gateway.confirm(PAYMENT, 10_000).status()).isEqualTo(PaymentStatus.UNKNOWN);
    }

    /** 비어 있거나 형식이 잘못된 PG 응답은 결과 미확정으로 남기는지 확인한다. */
    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "{invalid-json}", ""})
    void missingOrMalformedResponsesStayUnknown(String response) {
        server.expect(requestTo(BASE + "/confirm")).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        assertThat(gateway.confirm(PAYMENT, 10_000).status()).isEqualTo(PaymentStatus.UNKNOWN);
    }

    /** PG 상태가 DONE이어도 승인 시각이 없으면 성공으로 확정하지 않는지 확인한다. */
    @Test
    void doneWithoutApprovalTimeIsUnknown() {
        server.expect(requestTo(BASE + "/confirm")).andRespond(withSuccess(DONE.replace("\"2026-09-11T10:00:00+09:00\"", "null"), MediaType.APPLICATION_JSON));
        assertThat(gateway.confirm(PAYMENT, 10_000).status()).isEqualTo(PaymentStatus.UNKNOWN);
    }

    /** PG 조회에서 ABORTED 또는 EXPIRED를 확인하면 확정 실패로 해석하는지 확인한다. */
    @ParameterizedTest
    @ValueSource(strings = {"ABORTED", "EXPIRED"})
    void authoritativeTerminalFailureIsFailed(String status) {
        server.expect(requestTo(BASE + "/payment-key")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(DONE.replace("DONE", status), MediaType.APPLICATION_JSON));
        assertThat(gateway.lookup(PAYMENT, 10_000).status()).isEqualTo(PaymentStatus.FAILED);
    }

    /** 승인 요청의 명시적인 카드사 거절은 FAILED로 변환하는지 확인한다. */
    @ParameterizedTest
    @CsvSource({"403,REJECT_CARD_COMPANY", "400,INVALID_REJECT_CARD", "400,INVALID_STOPPED_CARD"})
    void explicitCardDeclineIsFailed(int status, String code) {
        server.expect(requestTo(BASE + "/confirm")).andRespond(withStatus(HttpStatus.valueOf(status))
                .contentType(MediaType.APPLICATION_JSON).body("{\"code\":\"" + code + "\"}"));
        assertThat(gateway.confirm(PAYMENT, 10_000)).isEqualTo(PaymentResult.failed(code));
    }

    /** 중복 처리·설정·서버 오류 등 승인 여부를 확정할 수 없는 오류를 UNKNOWN으로 유지하는지 확인한다. */
    @ParameterizedTest
    @CsvSource({"400,ALREADY_PROCESSED_PAYMENT", "409,IDEMPOTENT_REQUEST_PROCESSING", "401,UNAUTHORIZED_KEY", "500,REJECT_CARD_COMPANY", "429,TOO_MANY_REQUESTS", "400,UNKNOWN_NEW_ERROR", "404,NOT_FOUND_PAYMENT_SESSION"})
    void ambiguousErrorsStayUnknown(int status, String code) {
        server.expect(requestTo(BASE + "/confirm")).andRespond(withStatus(HttpStatus.valueOf(status))
                .contentType(MediaType.APPLICATION_JSON).body("{\"code\":\"" + code + "\"}"));
        assertThat(gateway.confirm(PAYMENT, 10_000)).isEqualTo(PaymentResult.unknown(code));
    }

    /** 결제창형의 간편결제는 카드·계좌·포인트를 사용할 수 있으므로 card 객체를 강제하지 않는다. */
    @ParameterizedTest
    @ValueSource(strings = {"null", "{\"amount\":10000}"})
    void easyPayApprovalSucceedsWithOrWithoutCard(String card) {
        String response = DONE.replace("카드", "간편결제")
                .replace("\"futureField\":\"ignored\"", "\"card\":" + card);
        server.expect(requestTo(BASE + "/confirm")).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        assertThat(gateway.confirm(PAYMENT, 10_000).status()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    /** 응답 타임아웃을 카드 거절로 간주하지 않고 통신 오류에 따른 UNKNOWN으로 처리하는지 확인한다. */
    @Test
    void timeoutDoesNotMeanDeclined() {
        server.expect(requestTo(BASE + "/confirm")).andRespond(withException(new SocketTimeoutException("response lost")));
        assertThat(gateway.confirm(PAYMENT, 10_000)).isEqualTo(PaymentResult.unknown("PG_COMMUNICATION_ERROR"));
    }

    /** PG 조회의 404 응답만으로 미결제를 확정하지 않는지 확인한다. */
    @Test
    void lookup404DoesNotMeanUnpaid() {
        server.expect(requestTo(BASE + "/payment-key")).andRespond(withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON).body("{\"code\":\"NOT_FOUND_PAYMENT\"}"));
        assertThat(gateway.lookup(PAYMENT, 10_000).status()).isEqualTo(PaymentStatus.UNKNOWN);
    }

    /** JSON이 아닌 오류 본문도 카드 거절로 단정하지 않는다. */
    @Test
    void malformedErrorBodyStaysUnknown() {
        server.expect(requestTo(BASE + "/confirm")).andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                .contentType(MediaType.TEXT_HTML).body("<html>gateway unavailable</html>"));
        assertThat(gateway.confirm(PAYMENT, 10_000).status()).isEqualTo(PaymentStatus.UNKNOWN);
    }

    /** 기존 결제 키의 GET 조회에서 일치하는 승인 결과를 받으면 성공으로 복구할 수 있는지 확인한다. */
    @Test
    void lookupRestoresSuccessfulPayment() {
        server.expect(requestTo(BASE + "/payment-key")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(DONE, MediaType.APPLICATION_JSON));
        assertThat(gateway.lookup(PAYMENT, 10_000).status()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    /** 같은 주문·키의 승인 금액 불일치는 별도 GET으로 확인한 뒤에만 취소 대상으로 돌려준다. */
    @Test
    void cancellationRequiresIndependentLookupOfTheSamePayment() {
        String mismatch = DONE.replace("10000", "9000");
        server.expect(requestTo(BASE + "/confirm")).andRespond(withSuccess(mismatch, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/payment-key")).andRespond(withSuccess(mismatch, MediaType.APPLICATION_JSON));
        assertThat(gateway.confirm(PAYMENT, 10_000).status()).isEqualTo(PaymentStatus.UNKNOWN);
        PaymentResult result = gateway.lookup(PAYMENT, 10_000);
        assertThat(result.status()).isEqualTo(PaymentStatus.CANCEL_PENDING);
        assertThat(result.pgAmount()).isEqualByComparingTo("9000");
    }

    @ParameterizedTest
    @CsvSource({"order-123,other-order", "payment-key,other-key"})
    void identityMismatchDoesNotAuthorizeCancelingAnUnrelatedPayment(String from, String to) {
        server.expect(requestTo(BASE + "/payment-key")).andRespond(withSuccess(DONE.replace(from, to), MediaType.APPLICATION_JSON));
        assertThat(gateway.lookup(PAYMENT, 10_000).status()).isEqualTo(PaymentStatus.REVIEW_REQUIRED);
    }

    @Test
    void fullCancelUsesPersistedIdempotencyKeyAndValidatesCancellationEvidence() {
        Payment payment = pendingCancel();
        server.expect(requestTo(BASE + "/payment-key/cancel")).andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", payment.getCancelIdempotencyKey()))
                .andExpect(content().json("""
                        {"cancelReason":"주문 금액 또는 통화 불일치로 자동 취소"}
                        """))
                .andRespond(withSuccess(canceledJson(), MediaType.APPLICATION_JSON));
        PaymentResult result = gateway.cancel(payment);
        assertThat(result.status()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(result.pgAmount()).isEqualByComparingTo("9000");
        assertThat(result.canceledAt()).hasToString("2026-09-11T01:01:00Z");
    }

    @ParameterizedTest
    @CsvSource({"CANCELED,DONE", "DONE,PENDING", "other-placeholder,unused"})
    void invalidCancelEvidenceCannotBeStoredAsCanceled(String from, String to) {
        String json = "other-placeholder".equals(from)
                ? canceledJson().replace("\"balanceAmount\":0", "\"balanceAmount\":100")
                : canceledJson().replace(from, to);
        server.expect(requestTo(BASE + "/payment-key/cancel")).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
        assertThat(gateway.cancel(pendingCancel()).status()).isEqualTo(PaymentStatus.UNKNOWN);
    }

    @Test
    void cancelTimeoutCanBeCheckedByImmediateLookupDespiteOriginalAmountMismatch() {
        Payment payment = pendingCancel();
        server.expect(requestTo(BASE + "/payment-key/cancel"))
                .andRespond(withException(new SocketTimeoutException("response lost")));
        server.expect(requestTo(BASE + "/payment-key"))
                .andRespond(withSuccess(canceledJson(), MediaType.APPLICATION_JSON));
        assertThat(gateway.cancel(payment).status()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCEL_PENDING);
        assertThat(gateway.lookup(payment, 10_000).status()).isEqualTo(PaymentStatus.CANCELED);
    }

    @Test
    void changedEvidenceAfterCancelIntentRequiresReview() {
        server.expect(requestTo(BASE + "/payment-key")).andRespond(withSuccess(DONE, MediaType.APPLICATION_JSON));
        assertThat(gateway.lookup(pendingCancel(), 10_000).status()).isEqualTo(PaymentStatus.REVIEW_REQUIRED);
    }

    /** 취소 의도가 저장된 뒤에도 토스가 DONE이면 취소 완료나 정상 결제 성공으로 처리하지 않는다. */
    @Test
    void stillApprovedAfterCancelIntentIsNotSucceeded() {
        server.expect(requestTo(BASE + "/payment-key"))
                .andRespond(withSuccess(DONE.replace("10000", "9000"), MediaType.APPLICATION_JSON));
        assertThat(gateway.lookup(pendingCancel(), 10_000).status()).isEqualTo(PaymentStatus.CANCEL_PENDING);
    }

    /** 취소를 시작한 결제에서 실패 상태가 조회되면 단순 결제 실패로 덮어쓰지 않는다. */
    @ParameterizedTest
    @ValueSource(strings = {"ABORTED", "EXPIRED"})
    void failureAfterCancelIntentRequiresReview(String status) {
        server.expect(requestTo(BASE + "/payment-key"))
                .andRespond(withSuccess(DONE.replace("10000", "9000").replace("DONE", status), MediaType.APPLICATION_JSON));
        assertThat(gateway.lookup(pendingCancel(), 10_000).status()).isEqualTo(PaymentStatus.REVIEW_REQUIRED);
    }

    private Payment pendingCancel() {
        Payment payment = new Payment("order-123", "payment-key");
        payment.applyResult(new PaymentResult(PaymentStatus.CANCEL_PENDING, "DONE", "PG_AMOUNT_MISMATCH",
                java.time.Instant.parse("2026-09-11T01:00:00Z"), java.math.BigDecimal.valueOf(9000), "KRW", null));
        return payment;
    }

    private String canceledJson() {
        return """
                {"orderId":"order-123","paymentKey":"payment-key","totalAmount":9000,"currency":"KRW",
                 "status":"CANCELED","method":"카드","approvedAt":"2026-09-11T10:00:00+09:00",
                 "balanceAmount":0,"cancels":[{"cancelStatus":"DONE","canceledAt":"2026-09-11T10:01:00+09:00"}]}
                """;
    }
}
