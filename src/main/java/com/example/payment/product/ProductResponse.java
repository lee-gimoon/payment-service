package com.example.payment.product;

import java.util.List;
import java.util.Map;

public record ProductResponse(String id, String name, String subtitle, String category, long price,
                              String color, String stage, String artwork, String badge, List<SizeResponse> sizes) {
    /** @param remaining 사이즈별 남은 수량. 남은 수량은 공개하지 않고 품절 여부만 알린다. */
    public static ProductResponse of(Product product, Map<String, Integer> remaining) {
        List<SizeResponse> sizes = Product.SIZES.stream()
                .map(size -> new SizeResponse(size, remaining.getOrDefault(size, 0) <= 0)).toList();
        return new ProductResponse(product.getId(), product.getName(), product.getSubtitle(),
                product.getCategory(), product.getPrice(), product.getColor(), product.getStage(),
                product.getArtwork(), product.getBadge(), sizes);
    }

    public record SizeResponse(String size, boolean soldOut) {}
}
