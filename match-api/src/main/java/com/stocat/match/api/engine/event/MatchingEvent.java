package com.stocat.match.api.engine.event;

public sealed interface MatchingEvent permits OrderAddedEvent, OrderbookEvent {
    String symbol();
}