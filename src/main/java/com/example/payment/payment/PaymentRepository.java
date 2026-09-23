/* 파일 역할: payments 테이블에 결제 시도를 저장하고 주문번호로 읽는 JPA 저장소를 정의한다. */
package com.example.payment.payment;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA가 구현하는 결제 저장소다. Payment의 기본 키가 주문번호이므로 findById에 orderId를 전달한다. */
public interface PaymentRepository extends JpaRepository<Payment, String> {
}
