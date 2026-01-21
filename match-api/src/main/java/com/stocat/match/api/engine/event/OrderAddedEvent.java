package com.stocat.match.api.engine.event;

import com.stocat.match.domain.order.Order;

import java.time.LocalDateTime;

public record OrderAddedEvent(
        Order order
) implements MatchingEvent {
    @Override
    public String symbol() {
        return order.symbol();
    }
}