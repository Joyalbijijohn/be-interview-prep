package com.interview.prep.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record OrderRequest(
        @NotEmpty(message = "items must not be empty")
        @Size(max = 100, message = "an order can contain at most 100 items")
        List<@Valid OrderItemRequest> items) {
}
