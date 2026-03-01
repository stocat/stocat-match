package com.stocat.match.api.engine;

import com.stocat.match.domain.fill.Fill;
import com.stocat.match.domain.order.Order;
import com.stocat.match.domain.order.OrderType;
import com.stocat.match.domain.TradeSide;
import com.stocat.match.domain.orderbook.Orderbook;
import com.stocat.match.domain.orderbook.PriceLevel;
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
     * 주문 체결 시도 (매수/매도 통합)
     *
     * @return FILLED: 체결 성공, SKIP: 시간 제약, STOP: 가격 불일치
     */
    public MatchResult match(Order order, Orderbook orderbook) {
        if (order.createdAt().isAfter(orderbook.timestamp())) {
            return MatchResult.skip(order.quantity());
        }

        List<PriceLevel> priceLevels = order.side() == TradeSide.BUY ? orderbook.asks() : orderbook.bids();

        return matchOrder(order, priceLevels);
    }

    private MatchResult matchOrder(Order order, List<PriceLevel> priceLevels) {
        if (priceLevels == null || priceLevels.isEmpty()) {
            return MatchResult.stop(order.quantity());
        }

        List<Fill> fills = new ArrayList<>();
        BigDecimal remainingQuantity = order.quantity();

        for (PriceLevel level : priceLevels) {
            if (remainingQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            if (!canFillAtPrice(order, level)) {
                break;
            }

            BigDecimal fillQuantity = remainingQuantity.min(level.quantity());
            fills.add(createFill(order, level.price(), fillQuantity));

            remainingQuantity = remainingQuantity.subtract(fillQuantity);
        }

        if (fills.isEmpty()) {
            return MatchResult.stop(order.quantity());
        }

        return MatchResult.filled(fills, remainingQuantity);
    }

    private boolean canFillAtPrice(Order order, PriceLevel level) {
        if (order.type() == OrderType.MARKET) {
            return true;
        }

        if (order.type() == OrderType.LIMIT) {
            BigDecimal orderPrice = order.price();
            BigDecimal levelPrice = level.price();

            if (order.side() == TradeSide.BUY) {
                return orderPrice.compareTo(levelPrice) >= 0;
            }
            if (order.side() == TradeSide.SELL) {
                return orderPrice.compareTo(levelPrice) <= 0;
            }
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