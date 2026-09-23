package com.example.payment.gateway;

import com.example.payment.payment.Payment;
import com.example.payment.payment.PaymentResult;
import com.example.payment.payment.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.Instant;
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
            return readConfirmationResult(payment, amount, response);
        } catch (RestClientResponseException exception) { // 토스 서버가 4xx·5xx 오류 응답을 보낸 경우를 잡는다.
            return readConfirmationError(exception); // 오류 응답의 코드를 읽어 명시적 결제 거절이면 FAILED, 그 밖에는 재조회가 필요한 UNKNOWN으로 바꾼다.
        } catch (RestClientException exception) { // 그 밖의 연결·시간 초과·응답 변환 등의 오류를 잡는다.
            // 응답이 끊겼어도 토스에서 승인되었을 수 있으므로 실패라고 단정하지 않는다.
            return PaymentResult.unknown("PG_COMMUNICATION_ERROR");
        }
    }

    /** 토스 API 서버에 paymentKey로 GET 요청을 보내 결제 한 건을 조회하고, 응답을 PaymentResult로 바꾼다. */
    public PaymentResult lookup(Payment payment, long amount) {
        try {
            TossPaymentResponse response = client.get()
                    .uri("/v1/payments/{paymentKey}", payment.getPaymentKey())
                    .retrieve()
                    .body(TossPaymentResponse.class);
            return readLookupResult(payment, amount, response);
        } catch (RestClientException exception) {
            return PaymentResult.unknown("PG_LOOKUP_ERROR");
        }
    }

    /**
     * 토스 API 서버에 paymentKey로 POST /v1/payments/{paymentKey}/cancel 요청을 보내 결제 전액 취소를 요청한다.
     * 저장된 취소 멱등키를 헤더에 넣고, JSON 본문에 취소 사유(cancelReason)를 담아 보낸다.
     * 토스 응답에서 같은 결제의 전액 취소가 확인되면 CANCELED를 반환한다.
     * 응답을 확인할 수 없으면 UNKNOWN을 반환하며, 호출한 PaymentService가 토스에 GET으로 다시 조회한다.
     */
    public PaymentResult cancel(Payment payment) {
        if (payment.getStatus() != PaymentStatus.CANCEL_PENDING || payment.getCancelIdempotencyKey() == null
                || payment.getPgAmount() == null || payment.getPgCurrency() == null) {
            throw new IllegalStateException("취소 의도를 먼저 저장해야 합니다.");
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

    /** 승인 POST 응답을 해석한다. 금액이 다르면 UNKNOWN을 반환해 PaymentService가 GET으로 재조회하게 한다. */
    private PaymentResult readConfirmationResult(Payment payment, long amount, TossPaymentResponse response) {
        // 응답 본문이 없으면 승인 여부를 판단할 수 없다.
        if (response == null) return PaymentResult.unknown("PG_EMPTY_RESPONSE");
        // 주문번호나 결제키가 다르면 UNKNOWN을 반환해 저장된 결제키로 다시 조회하게 한다.
        if (!samePayment(payment, response)) return PaymentResult.unknown("PG_RESPONSE_MISMATCH");
        // 금액·통화가 없거나 형식이 잘못되면 응답을 믿고 처리할 수 없다.
        if (!hasValidAmountAndCurrency(response)) return PaymentResult.unknown("PG_INCOMPLETE_RESPONSE");
        if (response.status() == null) return PaymentResult.unknown("PG_RESULT_UNCONFIRMED");

        return switch (response.status()) {
            case "DONE" -> readConfirmedApproval(amount, response);
            case "ABORTED", "EXPIRED" -> failedResult(response);
            default -> PaymentResult.unknown("PG_RESULT_UNCONFIRMED");
        };
    }

    /** GET 재조회 응답을 해석한다. 같은 결제의 승인 금액·통화가 주문과 다르면 취소 필요 상태를 반환한다. */
    private PaymentResult readLookupResult(Payment payment, long amount, TossPaymentResponse response) {
        // 응답 본문이 없으면 결제 상태를 판단할 수 없다.
        if (response == null) return PaymentResult.unknown("PG_EMPTY_RESPONSE");
        // 재조회에서도 식별자가 다르면 다른 거래를 취소하지 않도록 사람 확인 대상으로 남긴다.
        if (!samePayment(payment, response)) return PaymentResult.reviewRequired("PG_IDENTITY_MISMATCH");
        // 금액·통화가 없거나 형식이 잘못되면 취소 여부를 결정할 수 없다.
        if (!hasValidAmountAndCurrency(response)) return PaymentResult.unknown("PG_INCOMPLETE_RESPONSE");
        // 취소 의도가 이미 저장됐다면 주문 금액 대신 저장된 취소 대상과 응답을 비교한다.
        if (payment.getCancelIdempotencyKey() != null) return readCancellationLookupResult(payment, response);
        if (response.status() == null) return PaymentResult.unknown("PG_RESULT_UNCONFIRMED");

        return switch (response.status()) {
            case "DONE" -> readLookedUpApproval(amount, response);
            case "CANCELED" -> canceledResult(response);
            case "PARTIAL_CANCELED" -> PaymentResult.reviewRequired("PG_PARTIAL_CANCEL_REVIEW");
            case "ABORTED", "EXPIRED" -> failedResult(response);
            default -> PaymentResult.unknown("PG_RESULT_UNCONFIRMED");
        };
    }

    /** 승인 POST 응답의 DONE은 지원 결제수단·금액·통화까지 맞을 때만 성공으로 처리한다. */
    private PaymentResult readConfirmedApproval(long amount, TossPaymentResponse response) {
        // 승인 시각이 없으면 DONE만으로 성공을 확정하지 않는다.
        if (response.approvedAt() == null) return PaymentResult.unknown("PG_RESULT_UNCONFIRMED");
        // 이 서비스는 카드와 간편결제만 지원한다.
        if (!supportsMethod(response)) return PaymentResult.unknown("PG_RESULT_UNCONFIRMED");
        // 최초 승인 응답의 금액·통화가 달라지면 GET으로 한 번 더 확인한다.
        if (!matchesOrderAmount(amount, response)) return PaymentResult.unknown("PG_RESPONSE_MISMATCH");
        return result(PaymentStatus.SUCCEEDED, null, response, null);
    }

    /** GET에서 확인한 DONE은 주문 금액·통화가 다를 때만 취소 필요 상태가 된다. */
    private PaymentResult readLookedUpApproval(long amount, TossPaymentResponse response) {
        // 승인 시각이 없으면 DONE만으로 성공이나 취소 필요를 확정하지 않는다.
        if (response.approvedAt() == null) return PaymentResult.unknown("PG_RESULT_UNCONFIRMED");
        // 지원하지 않는 결제수단은 자동 취소하지 않고 사람 확인 대상으로 남긴다.
        if (!supportsMethod(response)) return PaymentResult.reviewRequired("PG_UNSUPPORTED_METHOD");
        // 같은 결제의 승인 금액·통화가 주문과 다르면 취소 의도를 저장하도록 알린다.
        if (!matchesOrderAmount(amount, response)) return result(PaymentStatus.CANCEL_PENDING, "PG_AMOUNT_MISMATCH", response, null);
        return result(PaymentStatus.SUCCEEDED, null, response, null);
    }

    /** 취소 요청 후 GET으로 확인한 결제가 같은 취소 대상인지, 전액 취소됐는지 검사한다. */
    private PaymentResult readCancellationLookupResult(Payment payment, TossPaymentResponse response) {
        // 취소 전에 저장한 토스 승인 금액·통화와 달라졌다면 자동 판정을 멈춘다.
        if (!sameCancellationAmount(payment, response)) return PaymentResult.reviewRequired("PG_CANCEL_EVIDENCE_CHANGED");
        if (response.status() == null) return PaymentResult.unknown("PG_RESULT_UNCONFIRMED");

        return switch (response.status()) {
            case "CANCELED" -> canceledResult(response);
            case "PARTIAL_CANCELED" -> PaymentResult.reviewRequired("PG_PARTIAL_CANCEL_REVIEW");
            case "ABORTED", "EXPIRED" -> PaymentResult.reviewRequired("PG_CANCEL_STATE_CONFLICT");
            case "DONE" -> readStillApprovedCancellation(response);
            default -> PaymentResult.unknown("PG_RESULT_UNCONFIRMED");
        };
    }

    /** 취소 의도가 저장됐는데 토스에서 아직 DONE이면 취소 완료로 처리하지 않는다. */
    private PaymentResult readStillApprovedCancellation(TossPaymentResponse response) {
        if (response.approvedAt() == null) return PaymentResult.unknown("PG_RESULT_UNCONFIRMED");
        if (!supportsMethod(response)) return PaymentResult.reviewRequired("PG_UNSUPPORTED_METHOD");
        return result(PaymentStatus.CANCEL_PENDING, "PG_AMOUNT_MISMATCH", response, null);
    }

    /** 상태 판단에 필요한 토스 금액·통화 값이 있는지 확인한다. */
    private boolean hasValidAmountAndCurrency(TossPaymentResponse response) {
        return response.totalAmount() != null && response.totalAmount().signum() > 0
                && response.currency() != null && response.currency().matches("[A-Z]{3}");
    }

    private boolean supportsMethod(TossPaymentResponse response) {
        return "카드".equals(response.method()) || "간편결제".equals(response.method());
    }

    /** 토스 승인 금액·통화가 DB의 주문 금액·원화와 같은지 확인한다. */
    private boolean matchesOrderAmount(long amount, TossPaymentResponse response) {
        return response.totalAmount().compareTo(BigDecimal.valueOf(amount)) == 0
                && "KRW".equals(response.currency());
    }

    private PaymentResult failedResult(TossPaymentResponse response) {
        return new PaymentResult(PaymentStatus.FAILED, response.status(), "PG_" + response.status(),
                null, null, null, null);
    }

    /** 토스 응답의 주문번호와 결제키가 DB에 저장된 결제의 값과 모두 일치하는지 확인한다. */
    private boolean samePayment(Payment payment, TossPaymentResponse response) {
        return response != null && payment.getOrderId().equals(response.orderId())
                && payment.getPaymentKey().equals(response.paymentKey());
    }

    /** 취소 전에 DB에 저장한 토스 승인 금액·통화가 현재 토스 응답의 금액·통화와 같은지 확인한다. */
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

    /**
     * 토스 API가 보낸 결제 응답 JSON을 Java 객체로 변환해 받는 DTO다.
     * 승인·조회·취소 응답 중 우리 코드에서 사용하는 필드만 선언한다.
     * {@code @JsonIgnoreProperties(ignoreUnknown = true)}는 JSON에 이 record에 없는 필드가 있어도
     * Jackson이 오류를 내지 않고 그 필드를 무시하게 한다.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record TossPaymentResponse(String paymentKey, String orderId, BigDecimal totalAmount, String currency,
                               String status, String method, OffsetDateTime approvedAt,
                               BigDecimal balanceAmount, List<TossCancellation> cancels) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TossCancellation(String cancelStatus, OffsetDateTime canceledAt) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TossErrorResponse(String code) {}
}
