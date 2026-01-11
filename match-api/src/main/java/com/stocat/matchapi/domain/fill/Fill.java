package com.stocat.matchapi.domain.fill;

import com.stocat.matchapi.domain.TradeSide;

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