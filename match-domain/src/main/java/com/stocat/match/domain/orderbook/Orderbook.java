package com.stocat.match.domain.orderbook;

import java.time.LocalDateTime;
import java.util.List;

public record Orderbook(
        String symbol,
        List<PriceLevel> asks,
        List<PriceLevel> bids,
        LocalDateTime timestamp
) {
}