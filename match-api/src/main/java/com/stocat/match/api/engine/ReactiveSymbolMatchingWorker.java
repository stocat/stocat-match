package com.stocat.match.api.engine;

import com.stocat.match.domain.fill.Fill;
import com.stocat.match.domain.order.Order;
import com.stocat.match.domain.order.OrderRepository;
import com.stocat.match.domain.orderbook.Orderbook;
import com.stocat.match.domain.TradeSide;
import com.stocat.match.api.exception.MatchErrorCode;
import com.stocat.match.api.infrastructure.trade.TradeApiClient;
import com.stocat.match.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Reactive 종목별 매칭 워커
 * - 단일 종목의 체결 처리를 담당
 * - Reactor Scheduler (단일 스레드)로 순서 보장
 * - OrderRepository (Redis)를 통해 주문 관리
 */
@Slf4j
public class ReactiveSymbolMatchingWorker implements MatchingWorker {
    private final String symbol;
    private final OrderRepository orderRepository;
    private final MatchingEngine matchingEngine;
    private final TradeApiClient tradeApiClient;
    private final Scheduler scheduler;

    public ReactiveSymbolMatchingWorker(String symbol, OrderRepository orderRepository,
                                        MatchingEngine matchingEngine, TradeApiClient tradeApiClient,
                                        Scheduler scheduler) {
        this.symbol = symbol;
        this.orderRepository = orderRepository;
        this.matchingEngine = matchingEngine;
        this.tradeApiClient = tradeApiClient;
        this.scheduler = scheduler;
    }

    /**
     * 호가 이벤트 처리 (리액티브 체인)
     * - subscribeOn(scheduler)로 종목별 단일 스레드 순서 보장
     */
    @Override
    public Mono<Void> processOrderbook(Orderbook orderbook) {
        if (!orderbook.symbol().equals(this.symbol)) {
            return Mono.error(new ApiException(MatchErrorCode.SYMBOL_MISMATCH,
                    Map.of("expected", this.symbol, "actual", orderbook.symbol())));
        }

        return matchAllOrders(orderbook)
                .doOnNext(this::publishFills)
                .then()
                .subscribeOn(scheduler);
    }

    // === 리액티브 매칭 체인 ===

    /**
     * 매수/매도 체결 후 결과 통합
     */
    private Mono<List<Fill>> matchAllOrders(Orderbook orderbook) {
        return Flux.concat(
                        processOrders(TradeSide.BUY, orderbook),
                        processOrders(TradeSide.SELL, orderbook)
                )
                .flatMapIterable(Function.identity())
                .collectList();
    }

    /**
     * Batch fetch 기반 주문 체결 처리
     * - score 범위로 체결 가능한 주문을 한 번에 조회
     * - concatMap으로 순차 처리, takeWhile로 가격 불일치 시 중단
     */
    private Mono<List<Fill>> processOrders(TradeSide side, Orderbook orderbook) {
        BigDecimal matchPrice = getMatchPrice(side, orderbook);
        if (matchPrice == null) {
            return Mono.just(List.of());
        }

        return orderRepository.fetchMatchableOrders(symbol, side, matchPrice)
                .concatMap(order -> matchOrder(order, orderbook))
                .takeWhile(result -> !result.isStop())
                .flatMap(result -> Flux.fromIterable(result.fills()))
                .collectList();
    }

    /**
     * 단건 주문 매칭 + 후처리
     * - STOP: 가격 불일치 → 이후 주문도 불가
     * - SKIP: 시간 제약 → 다음 주문 계속
     * - 부분 체결: updateQuantity
     * - 완전 체결: remove(orderId) → CAS (Hash 삭제 성공 여부로 취소 감지)
     */
    // TODO: Tif 옵션 필요 시 구현
    private Mono<MatchResult> matchOrder(Order order, Orderbook orderbook) {
        MatchResult result = matchingEngine.match(order, orderbook);
        if (!result.isFilled()) {
            return Mono.just(result);
        }

        if (result.isPartiallyFilled()) {
            return orderRepository.updateQuantity(order, result.remainingQuantity())
                    .thenReturn(result);
        }

        return orderRepository.remove(order.id())
                .map(cancelledQuantity -> result)
                .defaultIfEmpty(MatchResult.skip(order.quantity()));
    }

    private BigDecimal getMatchPrice(TradeSide side, Orderbook orderbook) {
        if (side == TradeSide.BUY) {
            return orderbook.asks() != null && !orderbook.asks().isEmpty()
                    ? orderbook.asks().getFirst().price() : null;
        } else {
            return orderbook.bids() != null && !orderbook.bids().isEmpty()
                    ? orderbook.bids().getFirst().price() : null;
        }
    }

    private void publishFills(List<Fill> fills) {
        fills.forEach(tradeApiClient::sendFill);
    }

    // ===========================

    @Override
    public void shutdown() {
        scheduler.dispose();
    }

    @Override
    public String getSymbol() {
        return symbol;
    }
}
