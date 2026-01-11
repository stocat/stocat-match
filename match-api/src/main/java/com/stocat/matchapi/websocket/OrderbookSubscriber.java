package com.stocat.matchapi.websocket;

import com.stocat.matchapi.domain.orderbook.Orderbook;
import reactor.core.publisher.Flux;

/**
 * Orderbook 구독자
 * - 외부 거래소 WebSocket 연결
 * - 실시간 호가 수신 및 파싱
 * - 종목별로 별도의 WebSocket 연결
 */
public interface OrderbookSubscriber {
    Flux<Orderbook> subscribe(String symbol);

    void disconnect(String symbol);
}