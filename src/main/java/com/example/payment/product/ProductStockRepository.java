package com.example.payment.product;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductStockRepository extends JpaRepository<ProductStock, ProductStock.Key> {
    List<ProductStock> findAllByProductIdIn(Collection<String> productIds);

    // 남은 수량이 충분할 때만 뺀다. 바뀐 행이 없으면 재고가 부족하다.
    @Modifying
    @Query("update ProductStock s set s.quantity = s.quantity - :quantity "
            + "where s.productId = :productId and s.size = :size and s.quantity >= :quantity")
    int take(@Param("productId") String productId, @Param("size") String size, @Param("quantity") int quantity);

    @Modifying
    @Query("update ProductStock s set s.quantity = s.quantity + :quantity "
            + "where s.productId = :productId and s.size = :size")
    void putBack(@Param("productId") String productId, @Param("size") String size, @Param("quantity") int quantity);
}
