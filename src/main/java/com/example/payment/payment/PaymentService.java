package com.example.payment.payment;

import com.example.payment.api.error.ApiException;
import com.example.payment.config.TossProperties;
import com.example.payment.gateway.TossPaymentClient;
import com.example.payment.order.OrderItemRepository;
import com.example.payment.order.OrderRepository;
import com.example.payment.order.OrderResponse;
import com.example.payment.order.PurchaseOrder;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 결제수단 인증이 끝난 뒤 결제를 승인한다. confirm()을 위에서 아래로 읽으면 처리 순서를 볼 수 있다.
 * 준비·결과 저장은 각각 짧은 트랜잭션으로 끝내므로 토스 응답을 기다리는 동안 DB 잠금을 잡지 않는다.
 */
@Service
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentPreparationService preparation;
    private final PaymentSettlementService settlement;
    private final TossPaymentClient tossPaymentClient;
    private final TossProperties tossProperties;

    public PaymentService(OrderRepository orderRepository, OrderItemRepository orderItemRepository,
                          PaymentRepository paymentRepository,
                          PaymentAttemptRepository paymentAttemptRepository,
                          PaymentPreparationService preparation, PaymentSettlementService settlement,
                          TossPaymentClient tossPaymentClient, TossProperties tossProperties) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentRepository = paymentRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.preparation = preparation;
        this.settlement = settlement;
        this.tossPaymentClient = tossPaymentClient;
        this.tossProperties = tossProperties;
    }

    /** 주문 확인 → 금액 확인 → 승인 요청 정보 저장 → 토스 승인 → 승인 결과 저장. */
    public OrderResponse confirm(ConfirmPaymentRequest request) {
        // 1. 브라우저가 보낸 주문번호로 서버에 저장된 주문을 찾는다.
        PurchaseOrder order = findOrder(request.orderId());

        // 2. 결제 금액은 브라우저 값을 믿지 않고, 서버에 저장된 주문 금액과 비교한다.
        if (request.amount().compareTo(BigDecimal.valueOf(order.getAmount())) != 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AMOUNT_MISMATCH", "주문 금액과 결제 금액이 다릅니다.");
        }

        // 3. 같은 결제 키의 재요청은 이미 저장된 결과를 돌려준다.
        Payment existing = paymentRepository.findByPaymentKey(request.paymentKey()).orElse(null);
        if (existing != null) {
            if (!existing.getOrderId().equals(order.getId())
                    || (request.attemptId() != null && !existing.getAttemptId().equals(request.attemptId()))) {
                throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_CONFLICT", "다른 주문 또는 시도에 연결된 결제 키입니다.");
            }
            return response(order.getId(), existing);
        }
        requirePaymentConfig();

        // 4. PG 호출 전에 주문 잠금 아래에서 결제 거래와 시도를 원자적으로 저장한다.
        PaymentPreparationService.Prepared prepared = preparation.prepare(order.getId(), request.paymentKey(), request.attemptId());
        Payment payment = prepared.payment();
        if (!prepared.newPayment()) return response(order.getId(), payment);

        // 5. 결제수단 인증 결과로 토스에 최종 승인을 요청한다.
        PaymentResult result = tossPaymentClient.confirm(payment, order.getAmount());

        // 6. 승인 결과를 먼저 저장한다. 응답이 불확실하면 저장된 결제키로 즉시 재조회한다.
        payment = settlement.record(payment, result);
        if (result.status() == PaymentStatus.UNKNOWN) {
            payment = verifyAndCancelIfNeeded(payment, order.getAmount());
        }
        return response(order.getId(), payment);
    }

    /** 승인 결과가 불확실하면 같은 요청에서 GET 재조회 → 취소 의도 저장 → 취소 → 결과 저장을 이어서 수행한다. */
    private Payment verifyAndCancelIfNeeded(Payment payment, long amount) {
        // 1. 저장된 결제키로 토스에 다시 조회해 실제 승인 상태를 확인한다.
        PaymentResult result = tossPaymentClient.lookup(payment, amount);

        // 2. 승인 금액이나 통화가 주문과 다르면 취소 요청 전에 취소 의도와 실제 승인 정보를 저장한다.
        if (result.status() == PaymentStatus.CANCEL_PENDING) {
            payment = settlement.record(payment, result);

            // 3. 저장한 취소 멱등키로 토스에 취소를 요청한다.
            result = tossPaymentClient.cancel(payment);

            // 4. 취소 응답이 불확실하면 한 번 더 조회해 취소 여부를 확인한다.
            if (result.status() == PaymentStatus.UNKNOWN) {
                result = tossPaymentClient.lookup(payment, amount);
            }
        }

        // 5. 재조회 후에도 결과가 불확실하거나 취소가 완료되지 않았다면 수동 확인 대상으로 분류한다.
        if (result.status() == PaymentStatus.UNKNOWN || result.status() == PaymentStatus.CANCEL_PENDING) {
            result = PaymentResult.reviewRequired(result.errorCode() == null
                    ? "PG_RESULT_UNCONFIRMED" : result.errorCode());
        }

        // 6. 최종 결과를 결제 객체에 반영하고 DB에 저장한다.
        payment = settlement.record(payment, result);

        // 7. 수동 확인이 필요한 결제는 주문번호와 사유, 취소 요청 여부를 로그에 남긴다.
        if (payment.getStatus() == PaymentStatus.REVIEW_REQUIRED) {
            log.error("PAYMENT_REVIEW_REQUIRED orderId={} errorCode={} cancelRequested={}",
                    payment.getOrderId(), payment.getErrorCode(), payment.getCancelIdempotencyKey() != null);
        }

        // 8. 저장된 결제 상태를 호출한 confirm()에 돌려준다.
        return payment;
    }

    private PurchaseOrder findOrder(String orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "주문을 찾을 수 없습니다."));
    }

    private OrderResponse response(String orderId, Payment payment) {
        PurchaseOrder order = findOrder(orderId);
        return OrderResponse.of(order, orderItemRepository.findByOrderId(orderId), payment,
                paymentAttemptRepository.findFirstByOrderIdOrderByStartedAtDesc(orderId).orElse(null));
    }

    private void requirePaymentConfig() {
        if (!tossProperties.configured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_NOT_CONFIGURED", "토스 테스트 키를 설정해주세요.");
        }
    }
}
