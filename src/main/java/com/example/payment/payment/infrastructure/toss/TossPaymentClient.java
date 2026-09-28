package com.example.payment.payment.infrastructure.toss;

import com.example.payment.payment.domain.ApprovalRequest;
import com.example.payment.payment.domain.PaymentResult;
import com.example.payment.payment.domain.PaymentResult.Outcome;
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

@Component
public class TossPaymentClient {
    private final RestClient client;

    public TossPaymentClient(@Qualifier("tossRestClient") RestClient client) {
        this.client = client;
    }

    public PaymentResult confirm(ApprovalRequest approval) {
        try {
            TossPaymentResponse response = client.post()
                    .uri("/v1/payments/confirm")
                    .header("Idempotency-Key", approval.attemptId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("orderId", approval.orderId(), "paymentKey", approval.paymentKey(),
                            "amount", approval.amount()))
                    .retrieve()
                    .body(TossPaymentResponse.class);
            return readResult(approval, response, false);
        } catch (RestClientResponseException exception) {
            return readConfirmationError(exception);
        } catch (RestClientException exception) {
            return PaymentResult.unknown("PG_COMMUNICATION_ERROR");
        }
    }

    public PaymentResult lookup(ApprovalRequest approval) {
        try {
            TossPaymentResponse response = client.get()
                    .uri("/v1/payments/{paymentKey}", approval.paymentKey())
                    .retrieve()
                    .body(TossPaymentResponse.class);
            return readResult(approval, response, true);
        } catch (RestClientException exception) {
            return PaymentResult.unknown("PG_LOOKUP_ERROR");
        }
    }

    private PaymentResult readResult(ApprovalRequest approval, TossPaymentResponse response, boolean lookup) {
        if (response == null) return PaymentResult.unknown("PG_EMPTY_RESPONSE");
        if (!approval.orderId().equals(response.orderId()) || !approval.paymentKey().equals(response.paymentKey())) {
            return lookup ? PaymentResult.reviewRequired("PG_IDENTITY_MISMATCH")
                    : PaymentResult.unknown("PG_RESPONSE_MISMATCH");
        }
        if (response.totalAmount() == null || response.totalAmount().signum() <= 0
                || response.currency() == null || !response.currency().matches("[A-Z]{3}")
                || response.status() == null) {
            return PaymentResult.unknown("PG_INCOMPLETE_RESPONSE");
        }

        return switch (response.status()) {
            case "DONE" -> readApproval(approval, response, lookup);
            case "ABORTED", "EXPIRED" -> result(Outcome.FAILED, "PG_" + response.status(), response);
            // 취소된 거래의 주문 처리는 수동 확인이 필요하다.
            case "CANCELED", "PARTIAL_CANCELED" -> lookup
                    ? result(Outcome.REVIEW_REQUIRED, "PG_UNEXPECTED_STATUS", response)
                    : PaymentResult.unknown("PG_RESULT_UNCONFIRMED");
            default -> PaymentResult.unknown("PG_RESULT_UNCONFIRMED");
        };
    }

    private PaymentResult readApproval(ApprovalRequest approval, TossPaymentResponse response, boolean lookup) {
        if (response.approvedAt() == null) return PaymentResult.unknown("PG_RESULT_UNCONFIRMED");
        if (!"카드".equals(response.method()) && !"간편결제".equals(response.method())) {
            return lookup ? result(Outcome.REVIEW_REQUIRED, "PG_UNSUPPORTED_METHOD", response)
                    : PaymentResult.unknown("PG_RESULT_UNCONFIRMED");
        }
        if (response.totalAmount().compareTo(BigDecimal.valueOf(approval.amount())) != 0
                || !approval.currency().equals(response.currency())) {
            return lookup ? result(Outcome.REVIEW_REQUIRED, "PG_AMOUNT_MISMATCH", response)
                    : PaymentResult.unknown("PG_RESPONSE_MISMATCH");
        }
        return result(Outcome.SUCCEEDED, null, response);
    }

    private PaymentResult result(Outcome outcome, String errorCode, TossPaymentResponse response) {
        return new PaymentResult(outcome, response.status(), errorCode,
                response.approvedAt() == null ? null : response.approvedAt().toInstant(),
                response.totalAmount(), response.currency());
    }

    private PaymentResult readConfirmationError(RestClientResponseException exception) {
        try {
            TossErrorResponse error = exception.getResponseBodyAs(TossErrorResponse.class);
            if (exception.getStatusCode().is4xxClientError() && error != null
                    && ("REJECT_CARD_COMPANY".equals(error.code())
                    || "INVALID_REJECT_CARD".equals(error.code())
                    || "INVALID_STOPPED_CARD".equals(error.code()))) {
                return PaymentResult.failed(error.code());
            }
            // HTTP 오류만으로 승인 실패를 확정할 수 없으므로 GET 재조회로 확인한다.
            if (error != null && error.code() != null && !error.code().isBlank() && error.code().length() <= 80) {
                return PaymentResult.unknown(error.code());
            }
        } catch (RestClientException ignored) {
            // 비정형 오류 응답도 결과 미확정으로 처리한다.
        }
        return PaymentResult.unknown("PG_HTTP_ERROR");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TossPaymentResponse(String paymentKey, String orderId, BigDecimal totalAmount, String currency,
                               String status, String method, OffsetDateTime approvedAt) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TossErrorResponse(String code) {}
}
