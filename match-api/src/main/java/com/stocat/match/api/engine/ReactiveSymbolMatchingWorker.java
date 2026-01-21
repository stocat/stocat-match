package com.stocat.match.api.engine;

import com.stocat.match.domain.fill.Fill;
import com.stocat.match.domain.order.Order;
import com.stocat.match.domain.orderbook.Orderbook;
import com.stocat.match.domain.fill.FillResult;
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
 * - OrderQueue 하나만 관리
 * - 이벤트 소스 시간 순서 보장
 */
@Slf4j
public class ReactiveSymbolMatchingWorker implements MatchingWorker {
    private final String symbol;
    private final OrderQueue orderQueue;
    private final MatchingEngine matchingEngine;
    private final Scheduler scheduler;
    private final Sinks.Many<MatchingEvent> eventSink;
    private Disposable disposable;

    public ReactiveSymbolMatchingWorker(String symbol, MatchingEngine matchingEngine) {
        this.symbol = symbol;
        this.orderQueue = new OrderQueue(symbol);
        this.matchingEngine = matchingEngine;
        this.scheduler = Schedulers.newSingle("reactive-matching-" + symbol, true);
        this.eventSink = Sinks.many().multicast().onBackpressureBuffer();

        initializeEventProcessor();
        log.info("ReactiveSymbolMatchingWorker 생성: symbol={}", symbol);
    }

    /**
     * 이벤트 프로세서 초기화
     */
    private void initializeEventProcessor() {
        this.disposable = eventSink.asFlux()
                .publishOn(scheduler)
                .subscribe(this::processEvent);
    }

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

    /**
     * 이벤트 처리
     */
    private void processEvent(MatchingEvent event) {
        switch (event) {
            case OrderAddedEvent orderEvent -> handleOrderAdded(orderEvent.order());
            case OrderbookEvent snapshotEvent -> handleSnapshot(snapshotEvent.orderbook());
        }
    }

    /**
     * 주문 추가 처리
     */
    private void handleOrderAdded(Order order) {
        orderQueue.addOrder(order);
        log.debug("주문 추가 완료: symbol={}, order={}", symbol, order);
    }

    /**
     * 호가 처리
     */
    private void handleSnapshot(Orderbook snapshot) {
        log.debug("호가 처리 시작: orderbook={}", snapshot);

        List<Fill> fills = new ArrayList<>();

        // 매수 주문 체결 처리
        fills.addAll(processBuyOrders(snapshot));

        // 매도 주문 체결 처리
        fills.addAll(processSellOrders(snapshot));

        // 체결 결과 발행
        if (!fills.isEmpty()) {
            publishFills(fills);
        }

        log.debug("호가 처리 완료: symbol={}, fillCount={}", symbol, fills.size());
    }

    /**
     * 매수 주문 체결 처리
     */
    private List<Fill> processBuyOrders(Orderbook snapshot) {
        List<Fill> fills = new ArrayList<>();
        List<Order> partiallyFilledOrders = new ArrayList<>();

        while (!orderQueue.isBuyOrdersEmpty()) {
            Order order = orderQueue.peekBuyOrder();

            FillResult result = matchingEngine.matchBuyOrder(order, snapshot);
            if (result.isEmpty()) {
                break;
            }

            // 큐에서 주문 제거
            orderQueue.pollBuyOrder();
            fills.addAll(result.fills());

            // 부분 체결인 경우 남은 주문을 리스트에 보관 (무한루프 방지)
            if (result.hasRemainingOrder()) {
                partiallyFilledOrders.add(result.remainingOrder());
            }
        }

        // 부분 체결된 주문들을 다시 큐에 추가 (다음 호가에서 처리)
        partiallyFilledOrders.forEach(orderQueue::addOrder);

        return fills;
    }

    /**
     * 매도 주문 체결 처리
     */
    private List<Fill> processSellOrders(Orderbook snapshot) {
        List<Fill> fills = new ArrayList<>();
        List<Order> partiallyFilledOrders = new ArrayList<>();

        while (!orderQueue.isSellOrdersEmpty()) {
            Order order = orderQueue.peekSellOrder();

            FillResult result = matchingEngine.matchSellOrder(order, snapshot);
            if (result.isEmpty()) {
                break;
            }

            // 큐에서 주문 제거
            orderQueue.pollSellOrder();
            fills.addAll(result.fills());

            // 부분 체결인 경우 남은 주문을 리스트에 보관 (무한루프 방지)
            if (result.hasRemainingOrder()) {
                partiallyFilledOrders.add(result.remainingOrder());
            }
        }

        // 부분 체결된 주문들을 다시 큐에 추가 (다음 호가에서 처리)
        partiallyFilledOrders.forEach(orderQueue::addOrder);

        return fills;
    }

    /**
     * 체결 결과 발행
     */
    private void publishFills(List<Fill> fills) {
        fills.forEach(fill -> log.info("Fill 발행: {}", fill));
    }

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