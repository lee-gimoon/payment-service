/* 파일 역할: 결제 작업의 선점과 결과 저장을 각각 짧은 DB 트랜잭션으로 처리한다. */
package com.example.payment.payment;

import com.example.payment.api.error.ApiException;
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

/**
 * 주문 행 잠금으로 결제의 중복·동시 실행을 제어하고 작업 식별자를 저장한다.
 * Spring 프록시를 통해 호출되는 공개 메서드는 REQUIRES_NEW에 따라 각각 별도 트랜잭션으로 실행된다.
 */
@Service
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class PaymentTransactions {
    private static final Duration PROCESSING_LEASE = Duration.ofSeconds(30);
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final Clock clock;
    private final TossProperties properties;

    /** 주문·결제 저장소, 처리 기한 계산용 시계, PG 설정을 주입받는다. */
    public PaymentTransactions(OrderRepository orders, PaymentRepository payments, Clock clock, TossProperties properties) {
        this.orders = orders;
        this.payments = payments;
        this.clock = clock;
        this.properties = properties;
    }

    /**
     * 주문을 잠그고 금액·기존 결제 키를 검사한다. 새 시도면 PROCESSING과 식별 정보를 저장한다.
     * 같은 키의 기존 시도면 PG를 다시 호출하지 않도록 command가 없는 Claim을 반환한다.
     */
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

    /** 재확인이 가능한 결제만 새 작업 식별자로 선점한다. 대상이 아니면 현재 주문 상태를 반환한다. */
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

    /**
     * 주문을 다시 잠그고 현재 작업의 PG 결과를 엔티티에 반영한다.
     * 조회한 Payment는 JPA가 관리하므로 별도의 save 호출 없이 트랜잭션 커밋 시 변경 내용이 저장된다.
     */
    public OrderResponse finish(PaymentCommand command, PaymentOutcome outcome) {
        PurchaseOrder order = lockOrder(command.orderId());
        Payment payment = payments.findById(command.orderId()).orElseThrow();
        payment.complete(command.operationId(), outcome, clock.instant());
        return OrderResponse.of(order, payment, clock.instant());
    }

    /** 저장된 식별 정보를 PG 호출 명령과 현재 주문 응답으로 묶어 트랜잭션 밖의 서비스에 전달한다. */
    private Claim claim(PurchaseOrder order, Payment payment) {
        PaymentCommand command = new PaymentCommand(payment.getOrderId(), payment.getPaymentKey(), payment.getAmount(),
                payment.getAttemptId(), payment.getOperationId());
        return new Claim(command, OrderResponse.of(order, payment, clock.instant()));
    }

    /** 주문 행에 쓰기 잠금을 걸어 조회하고, 없는 주문이면 404 업무 오류를 발생시킨다. */
    private PurchaseOrder lockOrder(String orderId) {
        return orders.findForUpdate(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "주문을 찾을 수 없습니다."));
    }

    /** PG 호출에 필요한 키가 없으면 작업을 저장하기 전에 결제 설정 오류를 발생시킨다. */
    private void requireConfigured() {
        if (!properties.configured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_NOT_CONFIGURED", "토스페이먼츠 테스트 키 설정이 필요합니다.");
        }
    }

    /** 작업 선점 결과다. command가 null이면 외부 PG 호출 없이 response를 그대로 반환해야 한다. */
    public record Claim(PaymentCommand command, OrderResponse response) {}
}
