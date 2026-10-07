package com.interview.prep.product;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank(message = "name is required")
        @Size(max = 150, message = "name must be at most 150 characters")
        String name,
        @NotBlank(message = "category is required")
        @Size(max = 50, message = "category must be at most 50 characters")
        String category,
        @NotNull(message = "price is required")
        @DecimalMin(value = "0.00", message = "price cannot be negative")
        @Digits(integer = 10, fraction = 2, message = "price must have at most 2 decimal places")
        BigDecimal price,
        @NotNull(message = "stock is required")
        @Min(value = 0, message = "stock cannot be negative")
        Integer stock,
        @NotNull(message = "rating is required")
        @DecimalMin(value = "0.0", message = "rating must be between 0 and 5")
        @DecimalMax(value = "5.0", message = "rating must be between 0 and 5")
        Double rating) {
}
