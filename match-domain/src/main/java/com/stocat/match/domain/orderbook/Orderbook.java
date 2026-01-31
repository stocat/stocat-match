package com.stocat.match.domain.orderbook;

import java.util.List;

public record Orderbook(
        String symbol,
        List<PriceLevel> asks, // 매도호가 (매수가격)
        List<PriceLevel> bids // 매수호가 (매도가격)
) {
    public Orderbook {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("가격은 0 이상이어야 합니다");
        }
        validateAsks(asks);
        validateBids(bids);
    }

    private void validateAsks(List<PriceLevel> asks) {
        if (asks == null || asks.isEmpty()) {
            return;
        }

        PriceLevel last = null;
        for (PriceLevel ask : asks) {
            if (last == null) {
                last = ask;
                continue;
            }

            if (ask.price().compareTo(last.price()) <= 0) {
                throw new IllegalArgumentException("매도호가는 오름차순으로 정렬되어 있어야 합니다.");
            }

            last = ask;
        }
    }

    private void validateBids(List<PriceLevel> bids) {
        if (bids == null || bids.isEmpty()) {
            return;
        }

        PriceLevel last = null;
        for (PriceLevel bid : bids) {
            if (last == null) {
                last = bid;
                continue;
            }

            if (bid.price().compareTo(last.price()) >= 0) {
                throw new IllegalArgumentException("매수호가는 내림차순으로 정렬되어 있어야 합니다.");
            }

            last = bid;
        }
    }
}