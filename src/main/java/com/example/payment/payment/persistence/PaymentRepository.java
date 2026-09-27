package com.example.payment.payment.persistence;

import com.example.payment.payment.domain.Payment;
import com.example.payment.payment.domain.PaymentStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, String> {
    Optional<Payment> findByPaymentKey(String paymentKey);
    Optional<Payment> findFirstByOrderIdOrderByCreatedAtDescIdDesc(String orderId);
    boolean existsByOrderIdAndStatusNot(String orderId, PaymentStatus status);
}
