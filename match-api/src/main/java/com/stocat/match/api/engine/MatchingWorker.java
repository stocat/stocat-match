package com.stocat.match.api.engine;

import com.stocat.match.domain.orderbook.Orderbook;
import reactor.core.publisher.Mono;

public interface MatchingWorker {
    String getSymbol();

    Mono<Void> processOrderbook(Orderbook orderbook);

    void shutdown();
}