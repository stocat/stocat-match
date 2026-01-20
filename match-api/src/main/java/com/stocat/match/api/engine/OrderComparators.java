package com.stocat.match.api.engine;

import com.stocat.match.domain.order.Order;
import com.stocat.match.domain.order.OrderType;

import java.math.BigDecimal;
import java.util.Comparator;

public class OrderComparators {

    /**
     * 매수 주문 정렬 우선순위: 시장가 우선 -> 지정가(LIMIT)는 가격 높은 순 -> 시간 우선
     */
    public static Comparator<Order> buyOrderComparator() {
        return Comparator.<Order>comparingInt(order -> order.type() == OrderType.MARKET ? 0 : 1)
                .thenComparing(
                        order -> getLimitPrice(order, BigDecimal.ZERO),
                        Comparator.reverseOrder()
                )
                .thenComparing(Order::createdAt);
    }


    /**
     * 매도 주문 정렬 우선순위: 시장가 우선 -> 지정가(LIMIT)는 가격 낮은 순 -> 시간 우선
     */
    public static Comparator<Order> sellOrderComparator() {
        return Comparator.<Order>comparingInt(order -> order.type() == OrderType.MARKET ? 0 : 1)
                .thenComparing(order -> getLimitPrice(order, BigDecimal.valueOf(Long.MAX_VALUE)))
                .thenComparing(Order::createdAt);
    }

    private static BigDecimal getLimitPrice(Order order, BigDecimal defaultValue) {
        if (order.type() == OrderType.LIMIT && order.price() != null) {
            return order.price();
        }
        return defaultValue;
    }
}