package com.stocat.matchdomain.orderbook;

import java.math.BigDecimal;


public record PriceLevel(
        BigDecimal price,
        BigDecimal quantity
) {
    public PriceLevel {
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("가격은 0 이상이어야 합니다");
        }
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("수량은 0 이상이어야 합니다");
        }
    }
}