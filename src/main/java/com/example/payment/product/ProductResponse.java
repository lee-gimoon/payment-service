package com.example.payment.product;

import java.util.List;
import java.util.Map;

public record ProductResponse(String id, String name, String subtitle, String category, long price,
                              String color, String stage, String artwork, String badge, List<SizeResponse> sizes) {
    // 한 옵션은 주문당 최대 10장이므로 화면에는 10까지만 알리고 실제 재고량은 공개하지 않는다.
    private static final int SHOWN_REMAINING_LIMIT = 10;

    /** @param remaining 사이즈별 남은 수량. 재고 행이 없는 사이즈는 품절이다. */
    public static ProductResponse of(Product product, Map<String, Integer> remaining) {
        List<SizeResponse> sizes = Product.SIZES.stream().map(size -> {
            int quantity = Math.max(0, remaining.getOrDefault(size, 0));
            return new SizeResponse(size, quantity == 0, Math.min(quantity, SHOWN_REMAINING_LIMIT));
        }).toList();
        return new ProductResponse(product.getId(), product.getName(), product.getSubtitle(),
                product.getCategory(), product.getPrice(), product.getColor(), product.getStage(),
                product.getArtwork(), product.getBadge(), sizes);
    }

    /** @param remaining 남은 수량. 10 이상이면 10이다. */
    public record SizeResponse(String size, boolean soldOut, int remaining) {}
}
