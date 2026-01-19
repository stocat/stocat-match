package com.stocat.matchapi.websocket;

import com.stocat.matchdomain.orderbook.Orderbook;
import reactor.core.publisher.Flux;

/**
 * Orderbook 구독자
 * - 실시간 호가 데이터 스트림 제공
 * - 구독 해제는 반환된 Flux의 Disposable로 관리 (Router 책임)
 */
public interface OrderbookSubscriber {
    Flux<Orderbook> subscribe(String symbol);
}