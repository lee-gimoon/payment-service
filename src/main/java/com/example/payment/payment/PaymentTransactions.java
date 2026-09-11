package com.example.payment.payment;

import com.example.payment.api.ApiException;
import com.example.payment.config.TossProperties;
import com.example.payment.gateway.PaymentGateway.PaymentCommand;
import com.example.payment.order.OrderRepository;
import com.example.payment.order.OrderResponse;
import com.example.payment.order.PurchaseOrder;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class PaymentTransactions {
    private static final Duration PROCESSING_LEASE = Duration.ofSeconds(30);
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final Clock clock;
    private final TossProperties properties;

    public PaymentTransactions(OrderRepository orders, PaymentRepository payments, Clock clock, TossProperties properties) {
        this.orders = orders;
        this.payments = payments;
        this.clock = clock;
        this.properties = properties;
    }

    public Claim claimConfirmation(ConfirmPaymentRequest request) {
        PurchaseOrder order = lockOrder(request.orderId());
        if (BigDecimal.valueOf(order.getAmount()).compareTo(request.amount()) != 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AMOUNT_MISMATCH", "주문 금액과 승인 요청 금액이 다릅니다.");
        }
        Payment existing = payments.findById(order.getId()).orElse(null);
        if (existing != null) {
            if (!existing.getPaymentKey().equals(request.paymentKey())) {
                throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_CONFLICT", "이미 다른 결제 시도가 연결된 주문입니다. 기존 결과를 조회해주세요.");
            }
            return new Claim(null, OrderResponse.of(order, existing, clock.instant()));
        }
        requireConfigured();
        Payment payment = Payment.start(order.getId(), request.paymentKey(), order.getAmount(), clock.instant(), PROCESSING_LEASE);
        payments.saveAndFlush(payment);
        return claim(order, payment);
    }

    public Claim claimReconciliation(String orderId) {
        PurchaseOrder order = lockOrder(orderId);
        Payment payment = payments.findById(orderId).orElse(null);
        if (payment == null || !payment.canReconcile(clock.instant())) {
            return new Claim(null, OrderResponse.of(order, payment, clock.instant()));
        }
        requireConfigured();
        payment.beginOperation(clock.instant(), PROCESSING_LEASE);
        return claim(order, payment);
    }

    public OrderResponse finish(PaymentCommand command, PaymentOutcome outcome) {
        PurchaseOrder order = lockOrder(command.orderId());
        Payment payment = payments.findById(command.orderId()).orElseThrow();
        payment.complete(command.operationId(), outcome, clock.instant());
        return OrderResponse.of(order, payment, clock.instant());
    }

    private Claim claim(PurchaseOrder order, Payment payment) {
        PaymentCommand command = new PaymentCommand(payment.getOrderId(), payment.getPaymentKey(), payment.getAmount(),
                payment.getAttemptId(), payment.getOperationId());
        return new Claim(command, OrderResponse.of(order, payment, clock.instant()));
    }

    private PurchaseOrder lockOrder(String orderId) {
        return orders.findForUpdate(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "주문을 찾을 수 없습니다."));
    }

    private void requireConfigured() {
        if (!properties.configured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_NOT_CONFIGURED", "토스페이먼츠 테스트 키 설정이 필요합니다.");
        }
    }

    public record Claim(PaymentCommand command, OrderResponse response) {}
}
