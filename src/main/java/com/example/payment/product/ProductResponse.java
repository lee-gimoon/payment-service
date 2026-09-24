package com.example.payment.product;

/** 상품 엔티티를 화면에 필요한 공개 정보로 변환한 응답이다. */
public record ProductResponse(String id, String name, String subtitle, String category, long price,
                              String color, String stage, String artwork, String badge) {
    public static ProductResponse of(Product product) {
        return new ProductResponse(product.getId(), product.getName(), product.getSubtitle(),
                product.getCategory(), product.getPrice(), product.getColor(), product.getStage(),
                product.getArtwork(), product.getBadge());
    }
}
