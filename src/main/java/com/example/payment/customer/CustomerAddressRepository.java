package com.example.payment.customer;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, String> {
    // 기본 배송지를 먼저, 나머지는 최근에 추가한 순서로 보여준다.
    List<CustomerAddress> findByCustomerIdOrderByDefaultAddressDescCreatedAtDesc(String customerId);

    // 같은 회원의 배송지 쓰기를 한 줄로 세워 개수 제한과 "기본 배송지 하나" 규칙을 지킨다.
    // 잠글 회원 행이 없으므로 회원 ID로 트랜잭션이 끝날 때 풀리는 advisory lock을 건다.
    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(hashtextextended(:customerId, 0))", nativeQuery = true)
    int lockCustomer(@Param("customerId") String customerId);
}
