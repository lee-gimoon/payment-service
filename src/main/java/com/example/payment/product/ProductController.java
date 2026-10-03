package com.example.payment.product;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Products")
public class ProductController {
    private final ProductCatalog catalog;
    private final ProductInventory inventory;

    public ProductController(ProductCatalog catalog, ProductInventory inventory) {
        this.catalog = catalog;
        this.inventory = inventory;
    }

    @GetMapping("/products")
    @Operation(summary = "판매 상품 조회")
    public List<ProductResponse> all() {
        List<Product> products = catalog.all();
        Map<String, Map<String, Integer>> remaining = inventory.remaining(products.stream().map(Product::getId).toList());
        return products.stream()
                .map(product -> ProductResponse.of(product, remaining.getOrDefault(product.getId(), Map.of())))
                .toList();
    }

    @GetMapping("/products/{id}")
    @Operation(summary = "상품 상세 조회")
    public ProductResponse get(@PathVariable String id) {
        Product product = catalog.getVisible(id);
        return ProductResponse.of(product, inventory.remaining(List.of(id)).getOrDefault(id, Map.of()));
    }
}
