package com.example.payment.gateway;

import com.example.payment.payment.Payment;
import com.example.payment.payment.PaymentResult;
import com.example.payment.payment.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** 토스와 HTTP로 통신한다. 주문·결제의 DB 저장은 PaymentService가 담당한다. */
@Component
public class TossPaymentClient {
    private final RestClient client;

    public TossPaymentClient(@Qualifier("tossRestClient") RestClient client) {
        this.client = client;
    }

    /** 카드 인증으로 받은 정보에 서버의 주문 금액을 넣어 최종 승인을 요청한다. */
    public PaymentResult confirm(Payment payment, long amount) {
        try {
            TossPaymentResponse response = client.post()
                    .uri("/v1/payments/confirm")
                    // 주문번호는 UUID로 생성된다. 같은 주문에는 항상 같은 멱등키를 보낸다.
                    .header("Idempotency-Key", payment.getOrderId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("orderId", payment.getOrderId(),
                            "paymentKey", payment.getPaymentKey(), "amount", amount))
                    .retrieve()
                    .body(TossPaymentResponse.class);
            return readResult(payment, amount, response);
        } catch (RestClientResponseException exception) {
            return readConfirmationError(exception);
        } catch (RestClientException exception) {
            // 응답이 끊겼어도 토스에서 승인되었을 수 있으므로 실패라고 단정하지 않는다.
            return PaymentResult.unknown("PG_COMMUNICATION_ERROR");
        }
    }

    /** 이미 보낸 결제의 결과만 조회한다. 새로운 승인을 요청하는 메서드가 아니다. */
    public PaymentResult lookup(Payment payment, long amount) {
        try {
            TossPaymentResponse response = client.get()
                    .uri("/v1/payments/{paymentKey}", payment.getPaymentKey())
                    .retrieve()
                    .body(TossPaymentResponse.class);
            return readResult(payment, amount, response);
        } catch (RestClientException exception) {
            return PaymentResult.unknown("PG_LOOKUP_ERROR");
        }
    }

    /** 요청한 주문·키·금액과 일치하며 승인 완료된 응답만 성공으로 처리한다. */
    private PaymentResult readResult(Payment payment, long amount, TossPaymentResponse response) {
        if (response == null
                || !payment.getOrderId().equals(response.orderId())
                || !payment.getPaymentKey().equals(response.paymentKey())
                || response.totalAmount() == null
                || response.totalAmount().compareTo(BigDecimal.valueOf(amount)) != 0
                || !"KRW".equals(response.currency())) {
            return PaymentResult.unknown("PG_RESPONSE_MISMATCH");
        }

        boolean cardPayment = "카드".equals(response.method())
                || ("간편결제".equals(response.method()) && response.card() != null);
        if ("DONE".equals(response.status()) && response.approvedAt() != null && cardPayment) {
            return new PaymentResult(PaymentStatus.SUCCEEDED, "DONE", null, response.approvedAt().toInstant());
        }
        if ("ABORTED".equals(response.status()) || "EXPIRED".equals(response.status())) {
            return new PaymentResult(PaymentStatus.FAILED, response.status(), "PG_" + response.status(), null);
        }
        return PaymentResult.unknown("PG_RESULT_UNCONFIRMED");
    }

    /** 명시적인 카드 거절만 실패로 처리한다. 그 밖의 오류는 조회로 다시 확인한다. */
    private PaymentResult readConfirmationError(RestClientResponseException exception) {
        try {
            TossErrorResponse error = exception.getResponseBodyAs(TossErrorResponse.class);
            if (exception.getStatusCode().is4xxClientError() && error != null
                    && ("REJECT_CARD_COMPANY".equals(error.code()) || "REJECT_CARD_PAYMENT".equals(error.code()))) {
                return PaymentResult.failed(error.code());
            }
        } catch (RestClientException ignored) {
            // 오류 응답이 JSON이 아니어도 결과 미확정으로 처리한다.
        }
        return PaymentResult.unknown("PG_HTTP_ERROR");
    }

    /** 토스의 JSON 응답 중 사용하는 필드만 받는다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record TossPaymentResponse(String paymentKey, String orderId, BigDecimal totalAmount, String currency,
                               String status, String method, OffsetDateTime approvedAt, Map<String, Object> card) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TossErrorResponse(String code) {}
}
