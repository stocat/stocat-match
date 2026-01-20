package com.stocat.match.api.engine;

import com.stocat.match.domain.order.Order;
import com.stocat.match.domain.orderbook.Orderbook;

public interface MatchingWorker {
    String getSymbol();

    void addOrder(Order order);
    void processOrderbook(Orderbook orderbook);

    void shutdown();
}
