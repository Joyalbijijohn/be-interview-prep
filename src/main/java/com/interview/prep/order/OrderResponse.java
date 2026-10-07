package com.interview.prep.order;

import java.time.Instant;
import java.util.List;

public record OrderResponse(Long id, OrderStatus status, List<Line> items, Instant createdAt) {

    public record Line(Long productId, int quantity) {
    }

    static OrderResponse from(Order order) {
        return new OrderResponse(order.getId(), order.getStatus(),
                order.getItems().stream().map(i -> new Line(i.getProductId(), i.getQuantity())).toList(),
                order.getCreatedAt());
    }
}
