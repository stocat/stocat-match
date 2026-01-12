package com.stocat.matchapi.engine;

import com.stocat.matchapi.domain.TradeSide;
import com.stocat.matchapi.domain.order.Order;
import lombok.extern.slf4j.Slf4j;

import java.util.PriorityQueue;

/**
 * 종목별 주문 관리 큐
 * - 단일 종목의 매수/매도 주문 데이터 관리
 * - 우선순위 큐로 주문 정렬 유지
 * - 각 종목당 하나의 인스턴스 생성
 * - Thread-safe 불필요 (단일 스레드 전용)
 */
@Slf4j
public class OrderQueue {
    private final String symbol;
    private final PriorityQueue<Order> buyOrders;
    private final PriorityQueue<Order> sellOrders;

    public OrderQueue(String symbol) {
        this.symbol = symbol;
        this.buyOrders = createBuyOrderQueue();
        this.sellOrders = createSellOrderQueue();
    }

    private PriorityQueue<Order> createBuyOrderQueue() {
        return new PriorityQueue<>(OrderComparators.buyOrderComparator());
    }

    private PriorityQueue<Order> createSellOrderQueue() {
        return new PriorityQueue<>(OrderComparators.sellOrderComparator());
    }

    /**
     * 주문 추가 (매수/매도 큐에 자동 분류)
     */
    public void addOrder(Order order) {
        if (!order.symbol().equals(this.symbol)) {
            throw new IllegalArgumentException(
                    String.format("종목 불일치: expected=%s, actual=%s", this.symbol, order.symbol())
            );
        }

        if (order.side() == TradeSide.BUY) {
            buyOrders.offer(order);
        } else if (order.side() == TradeSide.SELL) {
            sellOrders.offer(order);
        }
    }

    public Order peekBuyOrder() {
        return buyOrders.peek();
    }

    public Order peekSellOrder() {
        return sellOrders.peek();
    }

    public Order pollBuyOrder() {
        return buyOrders.poll();
    }

    public Order pollSellOrder() {
        return sellOrders.poll();
    }

    public boolean isBuyOrdersEmpty() {
        return buyOrders.isEmpty();
    }

    public boolean isSellOrdersEmpty() {
        return sellOrders.isEmpty();
    }

    public String getSymbol() {
        return symbol;
    }
}