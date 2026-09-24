package com.example.payment.order;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 주문번호로 purchase_order_items의 상품 항목들을 순서대로 읽는다. */
public interface OrderItemRepository extends JpaRepository<OrderItem, String> {
    @Query("select i from OrderItem i join fetch i.product where i.order.id = :orderId order by i.lineNumber")
    List<OrderItem> findByOrderId(@Param("orderId") String orderId);
}
