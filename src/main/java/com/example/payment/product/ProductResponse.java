package com.example.payment.product;

public record ProductResponse(String id, String name, String subtitle, String category, long price,
                              String color, String stage, String artwork, String badge) {
    public static ProductResponse of(Product product) {
        return new ProductResponse(product.getId(), product.getName(), product.getSubtitle(),
                product.getCategory(), product.getPrice(), product.getColor(), product.getStage(),
                product.getArtwork(), product.getBadge());
    }
}
