package com.stocat.match.api.engine;

import com.stocat.match.domain.order.Order;
import com.stocat.match.domain.order.OrderType;

import java.math.BigDecimal;
import java.util.Comparator;

public class OrderComparators {

    /**
     * 매수 주문 정렬 우선순위: 시장가 우선 -> 지정가(LIMIT)는 가격 높은 순 -> seq 순
     */
    public static Comparator<Order> buyOrderComparator() {
        return orderComparator(Comparator.reverseOrder());
    }

    /**
     * 매도 주문 정렬 우선순위: 시장가 우선 -> 지정가(LIMIT)는 가격 낮은 순 -> seq 순
     */
    public static Comparator<Order> sellOrderComparator() {
        return orderComparator(Comparator.naturalOrder());
    }

    private static Comparator<Order> orderComparator(Comparator<BigDecimal> priceComparator) {
        return Comparator.<Order>comparingInt(order -> order.type() == OrderType.MARKET ? 0 : 1)
                .thenComparing((o1, o2) -> {
                    if (o1.type() == OrderType.LIMIT && o2.type() == OrderType.LIMIT) {
                        return priceComparator.compare(o1.price(), o2.price());
                    }
                    return 0;
                })
                .thenComparing(Order::seq);
    }
}