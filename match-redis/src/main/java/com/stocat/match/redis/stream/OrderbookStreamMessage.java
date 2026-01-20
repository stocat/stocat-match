package com.stocat.match.redis.stream;

import com.stocat.match.domain.orderbook.Orderbook;

public record OrderbookStreamMessage(
        String recordId,
        String symbol,
        Orderbook orderbook,
        long timestamp
) {
}