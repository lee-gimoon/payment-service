/* 파일 역할: payments 테이블에 결제 시도를 저장하고 주문번호로 읽는 JPA 저장소를 정의한다. */
package com.example.payment.payment;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 결제 거래는 자체 ID를 갖고 한 주문에 여러 실패 거래를 남길 수 있다. */
public interface PaymentRepository extends JpaRepository<Payment, String> {
    Optional<Payment> findByPaymentKey(String paymentKey);
    Optional<Payment> findByAttemptId(String attemptId);
    Optional<Payment> findFirstByOrderIdOrderByCreatedAtDescIdDesc(String orderId);
    Optional<Payment> findFirstByOrderIdAndStatusNotOrderByCreatedAtDescIdDesc(String orderId, PaymentStatus status);
}
