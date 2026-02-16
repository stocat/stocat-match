package com.stocat.match.api.order.controller;

import com.stocat.match.domain.TradeSide;
import com.stocat.match.domain.order.Order;
import com.stocat.match.domain.order.OrderTif;
import com.stocat.match.domain.order.OrderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderRequest(
        @NotNull Long id,
        @NotBlank String symbol,
        @NotNull TradeSide side,
        @NotNull OrderType type,
        @NotNull @Positive BigDecimal quantity,
        BigDecimal price, // LIMIT 주문일 때만 필수 (Order 생성자에서 검증)
        @NotNull OrderTif tif
) {
    public Order toOrder() {
        return new Order(id, symbol, side, type, quantity, price, tif, LocalDateTime.now());
    }
}