package com.example.payment.product;

import com.example.payment.api.error.ApiException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductCatalog {
    private final ProductRepository repository;

    public ProductCatalog(ProductRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Product> all() {
        return repository.findAllByActiveTrueOrderByDisplayOrderAsc();
    }

    @Transactional(readOnly = true)
    public Product forOrder(String id) {
        return repository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "PRODUCT_NOT_FOUND", "판매 중인 상품을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public Product getVisible(String id) {
        return repository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "상품을 찾을 수 없습니다."));
    }
}
