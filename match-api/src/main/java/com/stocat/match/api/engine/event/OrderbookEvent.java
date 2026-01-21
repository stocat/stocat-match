package com.stocat.match.api.engine.event;

import com.stocat.match.domain.orderbook.Orderbook;

public record OrderbookEvent(
        Orderbook orderbook
) implements MatchingEvent {
    @Override
    public String symbol() {
        return orderbook.symbol();
    }
}