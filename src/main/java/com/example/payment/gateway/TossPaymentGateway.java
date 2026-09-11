package com.example.payment.gateway;

import com.example.payment.gateway.PaymentGateway.PaymentCommand;
import com.example.payment.payment.PaymentOutcome;
import com.example.payment.payment.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class TossPaymentGateway implements PaymentGateway {
    // 중복 승인, 인증 설정, 타임아웃 및 알 수 없는 오류는 거절로 확정하지 않는다.
    private static final Set<String> DEFINITE_DECLINES = Set.of("REJECT_CARD_COMPANY", "REJECT_CARD_PAYMENT");
    private final RestClient client;

    public TossPaymentGateway(@Qualifier("tossRestClient") RestClient client) { this.client = client; }

    @Override
    public PaymentOutcome confirm(PaymentCommand command) {
        return call(command, true, () -> client.post().uri("/v1/payments/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", command.attemptId())
                .body(Map.of("orderId", command.orderId(), "paymentKey", command.paymentKey(), "amount", command.amount()))
                .retrieve().body(PgPayment.class));
    }

    @Override
    public PaymentOutcome lookup(PaymentCommand command) {
        return call(command, false, () -> client.get().uri("/v1/payments/{paymentKey}", command.paymentKey())
                .retrieve().body(PgPayment.class));
    }

    private PaymentOutcome call(PaymentCommand command, boolean confirmation, Supplier<PgPayment> request) {
        try {
            return validate(command, request.get());
        } catch (RestClientResponseException exception) {
            PgError error = readError(exception);
            String code = error == null || error.code() == null || !error.code().matches("[A-Z0-9_]{1,80}")
                    ? "PG_HTTP_ERROR" : error.code();
            if (confirmation && exception.getStatusCode().is4xxClientError() && DEFINITE_DECLINES.contains(code)) {
                return PaymentOutcome.failed(code);
            }
            // 조회 404도 미승인 증거로 사용하지 않는다. PG 처리 중이거나 조회 응답이 늦을 수 있다.
            return PaymentOutcome.unknown(code);
        } catch (RestClientException exception) {
            return PaymentOutcome.unknown("PG_COMMUNICATION_ERROR");
        }
    }

    private PgError readError(RestClientResponseException exception) {
        try {
            return exception.getResponseBodyAs(PgError.class);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private PaymentOutcome validate(PaymentCommand command, PgPayment result) {
        if (result == null || !command.orderId().equals(result.orderId())
                || !command.paymentKey().equals(result.paymentKey()) || result.totalAmount() == null
                || result.totalAmount().compareTo(BigDecimal.valueOf(command.amount())) != 0
                || !"KRW".equals(result.currency())) {
            return PaymentOutcome.unknown("PG_RESPONSE_MISMATCH");
        }
        if ("DONE".equals(result.status()) && result.approvedAt() != null
                && ("카드".equals(result.method()) || ("간편결제".equals(result.method()) && result.card() != null))) {
            return new PaymentOutcome(PaymentStatus.SUCCEEDED, "DONE", null, result.approvedAt().toInstant());
        }
        if ("ABORTED".equals(result.status()) || "EXPIRED".equals(result.status())) {
            return new PaymentOutcome(PaymentStatus.FAILED, result.status(), "PG_" + result.status(), null);
        }
        // 인증만 완료된 IN_PROGRESS, 취소, 지원하지 않는 수단 등은 승인 완료로 저장하지 않는다.
        return new PaymentOutcome(PaymentStatus.UNKNOWN, safeStatus(result.status()), "PG_RESULT_UNCONFIRMED", null);
    }

    private String safeStatus(String status) {
        return status != null && status.matches("[A-Z_]{1,40}") ? status : null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PgPayment(String paymentKey, String orderId, BigDecimal totalAmount, String currency, String status,
                     String method, OffsetDateTime approvedAt, Map<String, Object> card) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record PgError(String code) {}
}
