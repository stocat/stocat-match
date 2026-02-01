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
        Long seq
) {
    public Order {
        if (type == OrderType.LIMIT && price == null) {
            throw new IllegalArgumentException("지정가 주문은 가격이 필수입니다");
        }
    }

    public Order withSeq(Long seq) {
        return new Order(
                this.id,
                this.symbol,
                this.side,
                this.type,
                this.quantity,
                this.price,
                this.tif,
                seq
        );
    }

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