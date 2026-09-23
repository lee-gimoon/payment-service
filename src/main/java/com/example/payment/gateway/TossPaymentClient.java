package com.example.payment.gateway;

import com.example.payment.payment.Payment;
import com.example.payment.payment.PaymentResult;
import com.example.payment.payment.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * 이 클래스는 우리 결제 서비스와 토스 결제 서버를 연결하는 외부 통신 게이트웨이 역할을 하는 클래스다.
 * 게이트웨이는 내부 서비스와 외부 시스템을 연결하는 관문을 뜻한다.
 * 게이트웨이를 두면 결제 로직이 토스의 URL·HTTP 요청 형식·오류 코드에 직접 의존하지 않아,
 * 토스 연동 방식이 바뀌어도 외부 통신 코드만 수정할 수 있다.
 * 즉, 이 클래스는 토스에 승인·조회·취소 요청을 보내고 응답과 오류를 PaymentResult로 변환한다.
 * 결제 처리 순서와 DB 저장은 PaymentService가 담당한다.
 */
@Component
public class TossPaymentClient {
    private final RestClient client;

    /**
     * Spring이 이 객체를 만들 때 RestClient 빈을 생성자 매개변수에 자동으로 주입한다.
     * {@code @Qualifier("tossRestClient")}는 RestClient 타입의 빈 중 토스 전용으로 설정한 빈을 선택한다.
     * 현재 프로젝트에는 RestClient 타입의 빈이 tossRestClient 하나뿐이라 이 어노테이션이 없어도 주입된다.
     */
    public TossPaymentClient(@Qualifier("tossRestClient") RestClient client) {
        this.client = client;
    }

    /**
     * 토스 결제창 인증 후 받은 paymentKey와 주문의 orderId·금액을
     * 토스의 결제 승인 API({@code POST /v1/payments/confirm})로 보내 최종 승인을 요청하는 메서드다.
     * 같은 주문의 중복 승인을 막도록 orderId를 멱등키로 보내며,
     * 토스의 성공·실패 응답이나 통신 오류를 내부 형식인 PaymentResult로 변환하여 반환한다.
     * 결제 결과를 DB에 저장하는 작업은 이 메서드가 아니라 PaymentService가 담당한다.
     */
    public PaymentResult confirm(Payment payment, long amount) {
        try {
            TossPaymentResponse response = client.post() // POST 요청의 설정 객체를 얻는다. 아직 서버로 보내지 않는다.
                    .uri("/v1/payments/confirm") // 미리 설정된 토스 서버 주소에 이 경로를 붙여 요청할 URL을 정한다.
                    .header("Idempotency-Key", payment.getOrderId()) // 같은 주문을 재요청할 때도 같은 orderId를 멱등키 헤더로 보낸다.
                    .contentType(MediaType.APPLICATION_JSON) // 요청 본문이 JSON임을 Content-Type 헤더에 표시한다.
                    .body(Map.of("orderId", payment.getOrderId(),
                            "paymentKey", payment.getPaymentKey(), "amount", amount)) // 이 값들을 보낼 본문으로 설정한다. 전송할 때 JSON으로 변환된다.
                    .retrieve() // 응답 처리용 객체를 얻는다. 이 객체에서 아래의 body(Class)를 호출할 수 있다.
                    .body(TossPaymentResponse.class); // 여기서 POST 요청을 전송하고 응답 JSON을 이 객체로 변환한다.
            return readResult(payment, amount, response, false);
        } catch (RestClientResponseException exception) { // 토스 서버가 4xx·5xx 오류 응답을 보낸 경우를 잡는다.
            return readConfirmationError(exception);
        } catch (RestClientException exception) { // 그 밖의 연결·시간 초과·응답 변환 등의 오류를 잡는다.
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
            return readResult(payment, amount, response, true);
        } catch (RestClientException exception) {
            return PaymentResult.unknown("PG_LOOKUP_ERROR");
        }
    }

    /** 저장된 취소 의도로 전액 취소한다. 응답이 끊기면 다음 작업이 먼저 조회한 뒤 같은 키로 재시도한다. */
    public PaymentResult cancel(Payment payment) {
        if (payment.getStatus() != PaymentStatus.CANCEL_PENDING || payment.getCancelIdempotencyKey() == null
                || payment.getPgAmount() == null || payment.getPgCurrency() == null) {
            throw new IllegalStateException("취소 의도를 먼저 저장해야 합니다.");
        }
        // 토스 멱등키의 유효 기간(15일)을 넘겨 맹목적으로 재전송하지 않는다.
        if (payment.getCancelRequestedAt().isBefore(Instant.now().minus(Duration.ofDays(14)))) {
            return PaymentResult.reviewRequired("PG_CANCEL_RETRY_EXPIRED");
        }
        try {
            TossPaymentResponse response = client.post()
                    .uri("/v1/payments/{paymentKey}/cancel", payment.getPaymentKey())
                    .header("Idempotency-Key", payment.getCancelIdempotencyKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("cancelReason", "주문 금액 또는 통화 불일치로 자동 취소"))
                    .retrieve()
                    .body(TossPaymentResponse.class);
            if (!samePayment(payment, response) || !sameCancellationAmount(payment, response)) {
                return PaymentResult.unknown("PG_CANCEL_RESPONSE_MISMATCH");
            }
            return canceledResult(response);
        } catch (RestClientException exception) {
            return PaymentResult.unknown("PG_CANCEL_UNCONFIRMED");
        }
    }

    /** 승인 응답은 먼저 검증하고, 별도 GET 재조회에서 같은 거래의 금액 불일치를 확인한 경우에만 취소를 준비한다. */
    private PaymentResult readResult(Payment payment, long amount, TossPaymentResponse response, boolean lookup) {
        if (response == null) return PaymentResult.unknown("PG_EMPTY_RESPONSE");
        if (!samePayment(payment, response)) {
            // 다른 주문의 결제를 취소하지 않도록 식별자 불일치는 운영자가 확인한다.
            return lookup ? PaymentResult.reviewRequired("PG_IDENTITY_MISMATCH")
                    : PaymentResult.unknown("PG_RESPONSE_MISMATCH");
        }
        if (response.totalAmount() == null || response.totalAmount().signum() <= 0
                || response.currency() == null || !response.currency().matches("[A-Z]{3}")) {
            return PaymentResult.unknown("PG_INCOMPLETE_RESPONSE");
        }
        if (payment.getCancelIdempotencyKey() != null && !sameCancellationAmount(payment, response)) {
            return PaymentResult.reviewRequired("PG_CANCEL_EVIDENCE_CHANGED");
        }
        if ("CANCELED".equals(response.status())) {
            return lookup ? canceledResult(response) : PaymentResult.unknown("PG_RESULT_UNCONFIRMED");
        }
        if ("PARTIAL_CANCELED".equals(response.status())) {
            return lookup ? PaymentResult.reviewRequired("PG_PARTIAL_CANCEL_REVIEW")
                    : PaymentResult.unknown("PG_RESULT_UNCONFIRMED");
        }
        if ("ABORTED".equals(response.status()) || "EXPIRED".equals(response.status())) {
            if (payment.getCancelIdempotencyKey() != null) return PaymentResult.reviewRequired("PG_CANCEL_STATE_CONFLICT");
            return new PaymentResult(PaymentStatus.FAILED, response.status(), "PG_" + response.status(), null);
        }
        boolean supportedMethod = "카드".equals(response.method()) || "간편결제".equals(response.method());
        if ("DONE".equals(response.status()) && response.approvedAt() != null) {
            if (!supportedMethod) {
                return lookup ? PaymentResult.reviewRequired("PG_UNSUPPORTED_METHOD")
                        : PaymentResult.unknown("PG_RESULT_UNCONFIRMED");
            }
            boolean amountMatches = response.totalAmount().compareTo(BigDecimal.valueOf(amount)) == 0
                    && "KRW".equals(response.currency());
            if (payment.getCancelIdempotencyKey() != null || !amountMatches) {
                if (!lookup) return PaymentResult.unknown("PG_RESPONSE_MISMATCH");
                return result(PaymentStatus.CANCEL_PENDING, "PG_AMOUNT_MISMATCH", response, null);
            }
            return result(PaymentStatus.SUCCEEDED, null, response, null);
        }
        return PaymentResult.unknown("PG_RESULT_UNCONFIRMED");
    }

    private boolean samePayment(Payment payment, TossPaymentResponse response) {
        return response != null && payment.getOrderId().equals(response.orderId())
                && payment.getPaymentKey().equals(response.paymentKey());
    }

    private boolean sameCancellationAmount(Payment payment, TossPaymentResponse response) {
        return response.totalAmount() != null && payment.getPgAmount() != null
                && response.totalAmount().compareTo(payment.getPgAmount()) == 0
                && payment.getPgCurrency().equals(response.currency());
    }

    /** 단순 HTTP 200이 아니라 전액 취소 상태·잔액·성공한 취소 이력을 확인한다. */
    private PaymentResult canceledResult(TossPaymentResponse response) {
        if (!"CANCELED".equals(response.status()) || response.balanceAmount() == null
                || response.balanceAmount().signum() != 0 || response.cancels() == null) {
            return PaymentResult.unknown("PG_CANCEL_UNCONFIRMED");
        }
        Instant canceledAt = response.cancels().stream()
                .filter(cancel -> cancel != null && "DONE".equals(cancel.cancelStatus()) && cancel.canceledAt() != null)
                .map(cancel -> cancel.canceledAt().toInstant()).max(Instant::compareTo).orElse(null);
        return canceledAt == null ? PaymentResult.unknown("PG_CANCEL_UNCONFIRMED")
                : result(PaymentStatus.CANCELED, null, response, canceledAt);
    }

    private PaymentResult result(PaymentStatus status, String error, TossPaymentResponse response, Instant canceledAt) {
        return new PaymentResult(status, response.status(), error,
                response.approvedAt() == null ? null : response.approvedAt().toInstant(),
                response.totalAmount(), response.currency(), canceledAt);
    }

    /** 명시적인 카드 거절만 실패로 처리한다. 그 밖의 오류는 조회로 다시 확인한다. */
    private PaymentResult readConfirmationError(RestClientResponseException exception) {
        try {
            TossErrorResponse error = exception.getResponseBodyAs(TossErrorResponse.class);
            if (exception.getStatusCode().is4xxClientError() && error != null
                    && ("REJECT_CARD_COMPANY".equals(error.code())
                    || "INVALID_REJECT_CARD".equals(error.code())
                    || "INVALID_STOPPED_CARD".equals(error.code()))) {
                return PaymentResult.failed(error.code());
            }
            // 이미 처리된 결제·설정 오류·세션 만료 등은 원인 코드를 보존하고 조회로 확인한다.
            if (error != null && error.code() != null && !error.code().isBlank() && error.code().length() <= 80) {
                return PaymentResult.unknown(error.code());
            }
        } catch (RestClientException ignored) {
            // 오류 응답이 JSON이 아니어도 결과 미확정으로 처리한다.
        }
        return PaymentResult.unknown("PG_HTTP_ERROR");
    }

    /** 토스의 JSON 응답 중 사용하는 필드만 받는다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record TossPaymentResponse(String paymentKey, String orderId, BigDecimal totalAmount, String currency,
                               String status, String method, OffsetDateTime approvedAt,
                               BigDecimal balanceAmount, List<TossCancellation> cancels) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TossCancellation(String cancelStatus, OffsetDateTime canceledAt) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TossErrorResponse(String code) {}
}
