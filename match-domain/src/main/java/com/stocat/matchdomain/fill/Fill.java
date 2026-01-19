package com.stocat.matchdomain.fill;

import com.stocat.matchdomain.TradeSide;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Fill(
        Long orderId,
        String symbol,
        TradeSide side,
        BigDecimal price,
        BigDecimal quantity,
        LocalDateTime filledAt
) {
}