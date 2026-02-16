package com.stocat.match.api.engine;

import com.stocat.match.domain.fill.Fill;

import java.math.BigDecimal;
import java.util.List;

/**
 * 체결 결과
 * - FILLED: 체결 성공 (부분/완전)
 * - SKIP: 시간 제약으로 체결 불가 (order.createdAt > orderbook.timestamp) → 다음 주문 계속
 * - STOP: 가격 불일치로 체결 불가 → 이후 주문도 불가
 */
public record MatchResult(
        MatchStatus status,
        List<Fill> fills,
        BigDecimal remainingQuantity
) {
    public enum MatchStatus { FILLED, SKIP, STOP }

    public boolean isFilled() { return status == MatchStatus.FILLED; }
    public boolean isSkip() { return status == MatchStatus.SKIP; }
    public boolean isStop() { return status == MatchStatus.STOP; }
    public boolean isFullyFilled() { return isFilled() && remainingQuantity.signum() == 0; }
    public boolean isPartiallyFilled() { return isFilled() && remainingQuantity.signum() > 0; }

    public static MatchResult filled(List<Fill> fills, BigDecimal remainingQuantity) {
        return new MatchResult(MatchStatus.FILLED, fills, remainingQuantity);
    }

    public static MatchResult skip(BigDecimal orderQuantity) {
        return new MatchResult(MatchStatus.SKIP, List.of(), orderQuantity);
    }

    public static MatchResult stop(BigDecimal orderQuantity) {
        return new MatchResult(MatchStatus.STOP, List.of(), orderQuantity);
    }
}