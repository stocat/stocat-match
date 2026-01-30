package com.stocat.match.api.engine;

import com.stocat.match.domain.fill.Fill;
import com.stocat.match.domain.fill.FillResult;
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
     * - 여러 호가 레벨에 걸쳐 체결 가능
     * - 각 가격 레벨마다 별도의 Fill 생성
     * - 부분 체결 시 남은 수량으로 주문 재생성
     *
     * @param order 체결할 주문
     * @param orderbook 현재 호가 정보
     * @return 체결 결과 (체결 내역, 총 체결 수량, 미체결 주문)
     */
    public FillResult match(Order order, Orderbook orderbook) {
        List<PriceLevel> priceLevels = order.side() == TradeSide.BUY ? orderbook.asks() : orderbook.bids();

        return matchOrder(order, priceLevels);
    }

    private FillResult matchOrder(Order order, List<PriceLevel> priceLevels) {
        if (priceLevels == null || priceLevels.isEmpty()) {
            return new FillResult(List.of(), BigDecimal.ZERO, order);
        }

        List<Fill> fills = new ArrayList<>();
        BigDecimal remainingQuantity = order.quantity();
        BigDecimal totalFilledQuantity = BigDecimal.ZERO;

        for (PriceLevel level : priceLevels) {
            if (remainingQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            if (!canFillAtPrice(order, level)) {
                break;
            }

            BigDecimal fillQuantity = remainingQuantity.min(level.quantity());
            Fill fill = createFill(order, level.price(), fillQuantity);
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
     * 주문이 특정 호가에서 체결 가능한지 확인
     */
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