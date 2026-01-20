package com.stocat.match.domain.fill;

import com.stocat.match.domain.TradeSide;

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