package com.interview.prep.product;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        Long id,
        String name,
        String category,
        BigDecimal price,
        int stock,
        double rating,
        Instant createdAt) {

    static ProductResponse from(Product product) {
        return new ProductResponse(product.getId(), product.getName(), product.getCategory(), product.getPrice(),
                product.getStock(), product.getRating(), product.getCreatedAt());
    }
}
