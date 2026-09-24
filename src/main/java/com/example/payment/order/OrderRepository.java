package com.example.payment.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

/** 주문을 저장하고 조회한다. save(), findById()의 구현은 Spring Data JPA가 제공한다. */
public interface OrderRepository extends JpaRepository<PurchaseOrder, String> {
    /** 응답을 만들 때만 항목을 함께 읽는다. 평소에는 항목을 지연 로딩한다. */
    @Query("select distinct o from PurchaseOrder o left join fetch o.items i left join fetch i.product where o.id = :id")
    Optional<PurchaseOrder> findWithItemsById(@Param("id") String id);
}
