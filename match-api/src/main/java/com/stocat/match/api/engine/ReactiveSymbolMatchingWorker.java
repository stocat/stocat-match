package com.stocat.match.api.engine;

import com.stocat.match.domain.fill.Fill;
import com.stocat.match.domain.fill.FillResult;
import com.stocat.match.domain.order.Order;
import com.stocat.match.domain.orderbook.Orderbook;
import com.stocat.match.domain.TradeSide;
import com.stocat.match.api.engine.event.MatchingEvent;
import com.stocat.match.api.engine.event.OrderAddedEvent;
import com.stocat.match.api.engine.event.OrderbookEvent;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;

/**
 * Reactive 종목별 매칭 워커
 * - 단일 종목의 체결 처리를 담당
 * - Reactor Scheduler (단일 스레드)로 순서 보장
 * - 담당 종목에 해당하는 OrderQueue 하나만 관리
 */
@Slf4j
public class ReactiveSymbolMatchingWorker implements MatchingWorker {
    private final String symbol;
    private final OrderQueue orderQueue;
    private final MatchingEngine matchingEngine;

    // 이벤트 처리를 위한 단일 스레드 스케줄러 (종목별 순서 보장)
    private final Scheduler scheduler;
    // 주문/호가 이벤트를 수신하여 순차 처리하기 위한 Reactor Sink (멀티 프로듀서 지원)
    private final Sinks.Many<MatchingEvent> eventSink;
    // shutdown 시 이벤트 구독 취소를 위한 Disposable
    private Disposable disposable;

    private long eventSeq = 0;

    public ReactiveSymbolMatchingWorker(String symbol, MatchingEngine matchingEngine) {
        this.symbol = symbol;
        this.orderQueue = new OrderQueue(symbol);
        this.matchingEngine = matchingEngine;
        this.scheduler = Schedulers.newSingle("reactive-matching-" + symbol, false);
        this.eventSink = Sinks.many().multicast().onBackpressureBuffer();

        initializeEventProcessor();
    }

    /**
     * 이벤트 프로세서 초기화
     */
    private void initializeEventProcessor() {
        this.disposable = eventSink.asFlux()
                .publishOn(scheduler)
                .subscribe(this::processEvent);
    }

    // === Thread-Safe (외부 스레드에서 호출) ===

    /**
     * 주문 추가
     */
    public void addOrder(Order order) {
        if (!order.symbol().equals(this.symbol)) {
            throw new IllegalArgumentException(
                    String.format("종목 불일치: expected=%s, actual=%s", this.symbol, order.symbol())
            );
        }

        eventSink.tryEmitNext(new OrderAddedEvent(order));
    }

    /**
     * 호가 처리
     */
    @Override
    public void processOrderbook(Orderbook orderbook) {
        if (!orderbook.symbol().equals(this.symbol)) {
            throw new IllegalArgumentException(
                    String.format("종목 불일치: expected=%s, actual=%s", this.symbol, orderbook.symbol())
            );
        }

        eventSink.tryEmitNext(new OrderbookEvent(orderbook));
    }

    // === Single-Threaded (scheduler 스레드에서만 실행) ===

    /**
     * 이벤트 처리
     */
    private void processEvent(MatchingEvent event) {
        switch (event) {
            case OrderAddedEvent orderEvent -> handleOrderAdded(orderEvent.order());
            case OrderbookEvent orderbookEvent -> handleOrderbook(orderbookEvent.orderbook());
        }
    }

    /**
     * 주문 추가 처리
     */
    private void handleOrderAdded(Order order) {
        order = order.withSeq(this.eventSeq);
        this.eventSeq += 1;
        orderQueue.addOrder(order);
    }

    /**
     * 호가 처리
     */
    private void handleOrderbook(Orderbook orderbook) {
        List<Fill> fills = new ArrayList<>();

        fills.addAll(processOrders(TradeSide.BUY, orderbook));
        fills.addAll(processOrders(TradeSide.SELL, orderbook));

        if (!fills.isEmpty()) {
            publishFills(fills);
        }
    }

    /**
     * 주문 체결 처리
     */
    private List<Fill> processOrders(TradeSide side, Orderbook orderbook) {
        List<Fill> fills = new ArrayList<>();
        List<Order> partiallyFilledOrders = new ArrayList<>();

        while (!orderQueue.isEmpty(side)) {
            Order order = orderQueue.peek(side);

            FillResult result = matchingEngine.match(order, orderbook);
            if (result.isEmpty()) {
                break;
            }

            orderQueue.poll(side);
            fills.addAll(result.fills());

            if (result.hasRemainingOrder()) {
                partiallyFilledOrders.add(result.remainingOrder());
            }
        }

        partiallyFilledOrders.forEach(orderQueue::addOrder);

        return fills;
    }

    /**
     * 체결 결과 발행
     */
    private void publishFills(List<Fill> fills) {
        fills.forEach(fill -> log.info("Fill 발행: {}", fill));
    }

    // ===========================

    /**
     * Graceful shutdown
     */
    public void shutdown() {

        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }

        eventSink.tryEmitComplete();
        scheduler.dispose();

    }

    public String getSymbol() {
        return symbol;
    }
}