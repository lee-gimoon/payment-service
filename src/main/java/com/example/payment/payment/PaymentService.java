package com.example.payment.payment;

import com.example.payment.api.error.ApiException;
import com.example.payment.config.TossProperties;
import com.example.payment.gateway.TossPaymentClient;
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
 * saveAndFlush() 호출마다 저장소가 트랜잭션을 처리하므로, 토스 응답을 기다리는 동안 DB 잠금을 잡지 않는다.
 */
@Service
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final TossPaymentClient tossPaymentClient;
    private final TossProperties tossProperties;

    public PaymentService(OrderRepository orderRepository, PaymentRepository paymentRepository,
                          TossPaymentClient tossPaymentClient, TossProperties tossProperties) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
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

        // 3. 같은 요청이 다시 오면 토스를 재호출하지 않고 저장된 결과를 반환한다.
        Payment payment = paymentRepository.findById(order.getId()).orElse(null);
        if (payment != null) {
            if (!payment.getPaymentKey().equals(request.paymentKey())) {
                throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_CONFLICT", "이미 다른 결제 요청이 연결된 주문입니다.");
            }
            return OrderResponse.of(order, payment);
        }
        requirePaymentConfig();

        // 4. 통신이 끊겨도 결과를 재확인할 수 있도록 주문번호와 paymentKey를 먼저 저장한다.
        payment = new Payment(order.getId(), request.paymentKey());
        payment = paymentRepository.saveAndFlush(payment);

        // 5. 결제수단 인증 결과로 토스에 최종 승인을 요청한다.
        PaymentResult result = tossPaymentClient.confirm(payment, order.getAmount());

        // 6. 승인 결과를 먼저 저장한다. 응답이 불확실하면 저장된 결제키로 즉시 재조회한다.
        payment.applyResult(result);
        payment = paymentRepository.saveAndFlush(payment);
        if (result.status() == PaymentStatus.UNKNOWN) {
            payment = verifyAndCancelIfNeeded(payment, order.getAmount());
        }
        return OrderResponse.of(order, payment);
    }

    /** 승인 결과가 불확실하면 같은 요청에서 GET 재조회 → 취소 의도 저장 → 취소 → 결과 저장을 이어서 수행한다. */
    private Payment verifyAndCancelIfNeeded(Payment payment, long amount) {
        PaymentResult result = tossPaymentClient.lookup(payment, amount);
        if (result.status() == PaymentStatus.CANCEL_PENDING) {
            payment.applyResult(result);
            payment = paymentRepository.saveAndFlush(payment); // 취소 멱등키와 실제 승인 금액을 외부 호출 전에 저장한다.
            result = tossPaymentClient.cancel(payment);
            if (result.status() == PaymentStatus.UNKNOWN) {
                // 취소 응답을 잃었다면 한 번 조회해 이미 취소되었는지 확인한다.
                result = tossPaymentClient.lookup(payment, amount);
            }
        }
        if (result.status() == PaymentStatus.UNKNOWN || result.status() == PaymentStatus.CANCEL_PENDING) {
            result = PaymentResult.reviewRequired(result.errorCode() == null
                    ? "PG_RESULT_UNCONFIRMED" : result.errorCode());
        }
        payment.applyResult(result);
        payment = paymentRepository.saveAndFlush(payment);
        if (payment.getStatus() == PaymentStatus.REVIEW_REQUIRED) {
            log.error("PAYMENT_REVIEW_REQUIRED orderId={} errorCode={} cancelRequested={}",
                    payment.getOrderId(), payment.getErrorCode(), payment.getCancelIdempotencyKey() != null);
        }
        return payment;
    }

    private PurchaseOrder findOrder(String orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "주문을 찾을 수 없습니다."));
    }

    private void requirePaymentConfig() {
        if (!tossProperties.configured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_NOT_CONFIGURED", "토스 테스트 키를 설정해주세요.");
        }
    }
}
