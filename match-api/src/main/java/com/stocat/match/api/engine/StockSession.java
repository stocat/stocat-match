package com.stocat.match.api.engine;

import reactor.core.Disposable;

public record StockSession(
        String symbol,
        MatchingWorker worker,
        Disposable subscription
) {
    void close() {
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
        }
        if (worker != null) {
            worker.shutdown();
        }
    }
}