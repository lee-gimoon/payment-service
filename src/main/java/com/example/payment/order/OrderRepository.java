package com.example.payment.order;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<PurchaseOrder, String> {
    // 같은 주문에 대한 결제 시도 생성과 승인 준비를 직렬화한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PurchaseOrder o where o.id = :id")
    Optional<PurchaseOrder> findByIdForUpdate(@Param("id") String id);

    // purchase_orders_customer_created_idx 인덱스로 회원의 최근 주문을 읽는다.
    List<PurchaseOrder> findTop50ByCustomerIdOrderByCreatedAtDesc(String customerId);

    // 관리자 주문 관리. 결제 완료 주문만 다룬다.
    @Query("select o from PurchaseOrder o where o.status = com.example.payment.order.OrderStatus.PAID "
            + "order by o.paidAt desc")
    List<PurchaseOrder> findPaid(Pageable page);

    // 송장을 기다리는 주문은 먼저 결제된 것부터 처리한다.
    @Query("select o from PurchaseOrder o where o.status = com.example.payment.order.OrderStatus.PAID "
            + "and not exists (select s.id from Shipment s where s.orderId = o.id) order by o.paidAt asc")
    List<PurchaseOrder> findPaidAwaitingShipment(Pageable page);

    @Query("select count(o) from PurchaseOrder o where o.status = com.example.payment.order.OrderStatus.PAID "
            + "and not exists (select s.id from Shipment s where s.orderId = o.id)")
    long countPaidAwaitingShipment();

    @Query("select o from PurchaseOrder o, Shipment s where s.orderId = o.id and s.status = :status "
            + "order by s.shippedAt desc")
    List<PurchaseOrder> findByShipmentStatus(@Param("status") ShipmentStatus status, Pageable page);
}
