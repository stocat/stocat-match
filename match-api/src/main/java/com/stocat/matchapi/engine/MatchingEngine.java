package com.stocat.matchapi.engine;

import com.stocat.matchapi.domain.fill.Fill;
import com.stocat.matchapi.domain.order.Order;
import com.stocat.matchapi.domain.order.OrderType;
import com.stocat.matchapi.domain.orderbook.Orderbook;
import com.stocat.matchapi.domain.orderbook.PriceLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class MatchingEngine {

    /**
     * 매수 주문 체결 시도
     * - 여러 호가 레벨에 걸쳐 체결 가능
     * - 각 가격 레벨마다 별도의 Fill 생성
     * - 부분 체결 시 남은 수량으로 주문 재생성
     */
    public FillResult matchBuyOrder(Order order, Orderbook orderbook) {
        if (orderbook.asks() == null || orderbook.asks().isEmpty()) {
            return FillResult.empty();
        }

        List<Fill> fills = new ArrayList<>();
        BigDecimal remainingQuantity = order.quantity();
        BigDecimal totalFilledQuantity = BigDecimal.ZERO;

        for (PriceLevel ask : orderbook.asks()) {
            if (remainingQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            if (!canFillBuyOrderAtPrice(order, ask)) {
                break;
            }

            BigDecimal fillQuantity = remainingQuantity.min(ask.quantity());

            Fill fill = createFill(order, ask.price(), fillQuantity);
            fills.add(fill);

            remainingQuantity = remainingQuantity.subtract(fillQuantity);
            totalFilledQuantity = totalFilledQuantity.add(fillQuantity);
        }

        // 부분 체결인 경우 남은 수량으로 주문 재생성
        Order remainingOrder = null;
        if (remainingQuantity.compareTo(BigDecimal.ZERO) > 0) {
            remainingOrder = order.withQuantity(remainingQuantity);
        }

        return new FillResult(fills, totalFilledQuantity, remainingOrder);
    }

    /**
     * 매도 주문 체결 시도
     * - 여러 호가 레벨에 걸쳐 체결 가능
     * - 각 가격 레벨마다 별도의 Fill 생성
     * - 부분 체결 시 남은 수량으로 주문 재생성
     */
    public FillResult matchSellOrder(Order order, Orderbook orderbook) {
        if (order.createdAt().isAfter(orderbook.timestamp())) {
            return FillResult.empty();
        }

        if (orderbook.bids() == null || orderbook.bids().isEmpty()) {
            return FillResult.empty();
        }

        List<Fill> fills = new ArrayList<>();
        BigDecimal remainingQuantity = order.quantity();
        BigDecimal totalFilledQuantity = BigDecimal.ZERO;

        for (PriceLevel bid : orderbook.bids()) {
            if (remainingQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            if (!canFillSellOrderAtPrice(order, bid)) {
                break;
            }

            BigDecimal fillQuantity = remainingQuantity.min(bid.quantity());

            Fill fill = createFill(order, bid.price(), fillQuantity);
            fills.add(fill);

            remainingQuantity = remainingQuantity.subtract(fillQuantity);
            totalFilledQuantity = totalFilledQuantity.add(fillQuantity);
        }

        Order remainingOrder = null;
        if (remainingQuantity.compareTo(BigDecimal.ZERO) > 0) {
            remainingOrder = order.withQuantity(remainingQuantity);
        }

        return new FillResult(fills, totalFilledQuantity, remainingOrder);
    }


    /**
     * 매수 주문이 특정 호가에서 체결 가능한지 확인
     */
    private boolean canFillBuyOrderAtPrice(Order order, PriceLevel ask) {
        if (order.type() == OrderType.MARKET) {
            return true;
        }

        if (order.type() == OrderType.LIMIT) {
            return order.price() != null && order.price().compareTo(ask.price()) >= 0;
        }

        return false;
    }

    /**
     * 매도 주문이 특정 호가에서 체결 가능한지 확인
     */
    private boolean canFillSellOrderAtPrice(Order order, PriceLevel bid) {
        if (order.type() == OrderType.MARKET) {
            return true;
        }

        if (order.type() == OrderType.LIMIT) {
            return order.price() != null && order.price().compareTo(bid.price()) <= 0;
        }

        return false;
    }

    private Fill createFill(Order order, BigDecimal price, BigDecimal quantity) {
        return new Fill(
                order.id(),
                order.symbol(),
                order.side(),
                price,
                quantity,
                LocalDateTime.now()
        );
    }
}