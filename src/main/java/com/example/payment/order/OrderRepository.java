package com.example.payment.order;

import org.springframework.data.jpa.repository.JpaRepository;

/** purchase_orders를 주문번호로 저장하고 조회한다. */
public interface OrderRepository extends JpaRepository<PurchaseOrder, String> {
}
