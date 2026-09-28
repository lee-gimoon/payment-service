package com.example.payment.payment.persistence;

import com.example.payment.payment.domain.PaymentAttempt;
import com.example.payment.payment.domain.PaymentAttemptStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, String> {
    Optional<PaymentAttempt> findFirstByOrderIdOrderByStartedAtDesc(String orderId);

    Optional<PaymentAttempt> findFirstByOrderIdAndApprovalRequestedAtNotNullOrderByApprovalRequestedAtDesc(String orderId);

    Optional<PaymentAttempt> findByPaymentKey(String paymentKey);

    // 주문 잠금을 먼저 잡기 위해 시도 엔티티를 읽지 않고 주문 ID만 조회한다.
    @Query("select a.orderId from PaymentAttempt a where a.id = :id")
    Optional<String> findOrderIdById(@Param("id") String id);

    @Query("""
            select a.id from PaymentAttempt a
            where a.status in :statuses and coalesce(a.lastCheckedAt, a.approvalRequestedAt) < :checkedBefore
            order by a.approvalRequestedAt
            """)
    List<String> findIdsToRecover(@Param("statuses") Collection<PaymentAttemptStatus> statuses,
                                  @Param("checkedBefore") Instant checkedBefore, Limit limit);
}
