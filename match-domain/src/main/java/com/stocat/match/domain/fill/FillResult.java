package com.stocat.match.domain.fill;

import com.stocat.match.domain.order.Order;

import java.math.BigDecimal;
import java.util.List;

/**
 * 체결 결과
 * - 체결된 Fill 리스트와 총 체결 수량 포함
 * - 부분 체결 시 남은 수량으로 재생성된 주문 포함
 */
public record FillResult(
        List<Fill> fills,
        BigDecimal totalFilledQuantity,
        Order remainingOrder  // null이면 완전 체결
) {
    public boolean isEmpty() {
        return fills.isEmpty();
    }

    public boolean hasRemainingOrder() {
        return remainingOrder != null;
    }
}