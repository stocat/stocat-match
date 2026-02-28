package com.stocat.match.domain.order;

import com.stocat.match.domain.TradeSide;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

/**
 * 주문 관리 저장소
 * - 매수/매도 주문 데이터 관리
 */
public interface OrderRepository {
    Mono<Void> addOrder(Order order);
    Flux<Order> fetchMatchableOrders(String symbol, TradeSide side, BigDecimal matchPrice);
    Mono<BigDecimal> remove(Long orderId);
    Mono<Void> updateQuantity(Order order, BigDecimal newQuantity);
    Mono<Boolean> isEmpty(String symbol, TradeSide side);
}