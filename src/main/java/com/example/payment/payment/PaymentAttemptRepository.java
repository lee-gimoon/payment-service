package com.example.payment.payment;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, String> {
    Optional<PaymentAttempt> findFirstByOrderIdOrderByStartedAtDesc(String orderId);
}
