package com.example.payment.product;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Products")
public class ProductController {
    private final ProductCatalog catalog;

    public ProductController(ProductCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/products")
    @Operation(summary = "판매 상품 조회")
    public List<ProductResponse> all() {
        return catalog.all().stream().map(ProductResponse::of).toList();
    }

    @GetMapping("/products/{id}")
    @Operation(summary = "상품 상세 조회")
    public ProductResponse get(@PathVariable String id) {
        return ProductResponse.of(catalog.getVisible(id));
    }
}
