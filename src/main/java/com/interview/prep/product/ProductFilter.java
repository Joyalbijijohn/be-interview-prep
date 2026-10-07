package com.interview.prep.product;

import java.math.BigDecimal;

public record ProductFilter(String category, BigDecimal minPrice, BigDecimal maxPrice, boolean inStockOnly,
        String nameQuery) {
}
