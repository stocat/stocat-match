package com.stocat.match.domain.order;

import com.stocat.match.domain.TradeSide;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Order(
        Long id,
        String symbol,
        TradeSide side,
        OrderType type,
        BigDecimal quantity,
        BigDecimal price,
        OrderTif tif,
        LocalDateTime createdAt
) {
    public Order withQuantity(BigDecimal quantity) {
        return new Order(
                this.id,
                this.symbol,
                this.side,
                this.type,
                quantity,
                this.price,
                this.tif,
                this.createdAt
        );
    }
}