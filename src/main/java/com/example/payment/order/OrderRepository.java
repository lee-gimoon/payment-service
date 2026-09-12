/* 파일 역할: purchase_orders 테이블을 읽고 저장하는 JPA 저장소 계약을 정의한다. */
package com.example.payment.order;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA가 구현하는 주문 저장소다. 일반 CRUD와 결제 처리용 잠금 조회를 제공한다. */
public interface OrderRepository extends JpaRepository<PurchaseOrder, String> {
    /** 같은 주문의 결제 작업을 순서대로 처리하도록 트랜잭션이 끝날 때까지 주문 행에 쓰기 잠금을 건다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PurchaseOrder o where o.id = :id")
    Optional<PurchaseOrder> findForUpdate(@Param("id") String id);
}
