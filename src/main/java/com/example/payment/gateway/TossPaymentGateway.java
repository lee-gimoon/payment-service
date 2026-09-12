/* 파일 역할: 토스 결제 REST API를 호출하고 응답·오류를 우리 서비스의 결제 결과로 변환한다. */
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

/** 토스 HTTP 통신과 응답 검증을 담당하는 PaymentGateway 구현체다. DB에는 직접 접근하지 않는다. */
@Component
public class TossPaymentGateway implements PaymentGateway {
    // 중복 승인, 인증 설정, 타임아웃 및 알 수 없는 오류는 거절로 확정하지 않는다.
    private static final Set<String> DEFINITE_DECLINES = Set.of("REJECT_CARD_COMPANY", "REJECT_CARD_PAYMENT");
    private final RestClient client;

    /** 인증 헤더와 연결·응답 제한 시간이 설정된 토스 전용 HTTP 클라이언트를 주입받는다. */
    public TossPaymentGateway(@Qualifier("tossRestClient") RestClient client) { this.client = client; }

    /** 결제 시도 식별자를 Idempotency-Key로 보내 POST /v1/payments/confirm을 호출한다. */
    @Override
    public PaymentOutcome confirm(PaymentCommand command) {
        return call(command, true, () -> client.post().uri("/v1/payments/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", command.attemptId())
                .body(Map.of("orderId", command.orderId(), "paymentKey", command.paymentKey(), "amount", command.amount()))
                .retrieve().body(PgPayment.class));
    }

    /** GET /v1/payments/{paymentKey}로 기존 결제 결과를 조회하여 불명확한 상태를 재확인한다. */
    @Override
    public PaymentOutcome lookup(PaymentCommand command) {
        return call(command, false, () -> client.get().uri("/v1/payments/{paymentKey}", command.paymentKey())
                .retrieve().body(PgPayment.class));
    }

    /**
     * 전달받은 HTTP 요청을 실행하고 정상 응답은 검증, 예외는 내부 결제 결과로 변환한다.
     * 승인 요청의 명시적인 카드 거절만 FAILED로 확정하고, 그 밖의 HTTP·통신 오류는 UNKNOWN으로 남긴다.
     */
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

    /** PG의 오류 JSON에서 코드를 읽고, 본문 형식을 해석할 수 없으면 null을 반환한다. */
    private PgError readError(RestClientResponseException exception) {
        try {
            return exception.getResponseBodyAs(PgError.class);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /**
     * 주문번호·키·금액·통화의 일치를 검사한 뒤 PG 상태와 카드 수단·승인 시각으로 내부 상태를 결정한다.
     * HTTP 요청이 성공했더라도 결제 정보가 다르거나 승인 완료를 확인할 수 없으면 UNKNOWN을 반환한다.
     */
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

    /** 저장할 수 있는 길이와 문자 형식의 PG 상태만 통과시키고 나머지는 null로 바꾼다. */
    private String safeStatus(String status) {
        return status != null && status.matches("[A-Z_]{1,40}") ? status : null;
    }

    /** PG 응답에서 검증에 필요한 필드만 읽는 DTO다. 새로 추가된 알 수 없는 필드는 무시한다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record PgPayment(String paymentKey, String orderId, BigDecimal totalAmount, String currency, String status,
                     String method, OffsetDateTime approvedAt, Map<String, Object> card) {}
    /** PG 오류 본문에서 내부 처리에 사용할 오류 코드만 읽는다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record PgError(String code) {}
}
