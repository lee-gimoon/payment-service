package com.example.payment.product;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, String> {
    List<Product> findAllByActiveTrueOrderByDisplayOrderAsc();
    Optional<Product> findByIdAndActiveTrue(String id);
}
