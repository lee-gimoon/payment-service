package com.example.payment.order;

import org.springframework.data.jpa.repository.JpaRepository;

/** 주문을 저장하고 조회한다. save(), findById()의 구현은 Spring Data JPA가 제공한다. */
public interface OrderRepository extends JpaRepository<PurchaseOrder, String> {
}
