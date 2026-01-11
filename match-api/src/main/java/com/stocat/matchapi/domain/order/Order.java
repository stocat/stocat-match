package com.stocat.matchapi.domain.order;

import com.stocat.matchapi.domain.TradeSide;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Order(
        Long id,
        Long userId,
        String symbol,
        TradeSide side,
        OrderType type,
        BigDecimal quantity,
        BigDecimal price,
        OrderTif tif,
        LocalDateTime createdAt
) {
}