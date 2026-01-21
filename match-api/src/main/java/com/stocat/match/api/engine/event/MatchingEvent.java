package com.stocat.match.api.engine.event;

public sealed interface MatchingEvent permits OrderAddedEvent, OrderbookEvent {
    /**
     * 종목 코드
     */
    String symbol();
}