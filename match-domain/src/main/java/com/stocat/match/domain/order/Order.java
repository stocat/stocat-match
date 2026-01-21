package com.stocat.match.domain.order;

import com.stocat.match.domain.TradeSide;

import java.math.BigDecimal;

public record Order(
        Long id,
        String symbol,
        TradeSide side,
        OrderType type,
        BigDecimal quantity,
        BigDecimal price,
        OrderTif tif,
        String seq
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
                this.seq
        );
    }
}