package com.example.payment.product;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** DB에 저장된 판매 상품을 읽는다. */
public interface ProductRepository extends JpaRepository<Product, String> {
    List<Product> findAllByActiveTrueOrderByDisplayOrderAsc();
    Optional<Product> findByIdAndActiveTrue(String id);
}
