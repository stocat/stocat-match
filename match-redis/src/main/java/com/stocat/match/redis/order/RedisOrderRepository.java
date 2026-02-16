package com.stocat.match.redis.order;

import com.stocat.match.domain.TradeSide;
import com.stocat.match.domain.order.Order;
import com.stocat.match.domain.order.OrderRepository;
import com.stocat.match.domain.order.OrderType;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.ZoneOffset;

/**
 * Redis ZSET + HASH 기반 OrderRepository 구현
 * - OrderRankingStore(ZSET)와 OrderDetailStore(HASH)를 조합
 * - score 계산, member 포맷 변환 등의 변환 로직 담당
 */
@Component
public class RedisOrderRepository implements OrderRepository {

    private static final String MEMBER_FORMAT = "%015d|%d";
    private static final double BUY_MARKET_SCORE = -Double.MAX_VALUE;
    private static final double SELL_MARKET_SCORE = -1;

    private final OrderZsetClient rankingStore;
    private final OrderHashClient detailStore;
    private final long priceScale;

    public RedisOrderRepository(OrderZsetClient rankingStore,
                                OrderHashClient detailStore, RedisOrderQueueProperties orderQueueProperties) {
        this.rankingStore = rankingStore;
        this.detailStore = detailStore;
        this.priceScale = orderQueueProperties.priceScale();
    }

    @Override
    public Mono<Void> addOrder(Order order) {
        double score = toScore(order);
        String member = toMember(order);
        String side = sideKey(order.side());

        return rankingStore.add(side, order.symbol(), score, member)
                .then(detailStore.save(order));
    }

    @Override
    public Flux<Order> fetchMatchableOrders(String symbol, TradeSide side, BigDecimal matchPrice) {
        double maxScore = toMaxScore(side, matchPrice);
        return rankingStore.rangeMembersByScore(sideKey(side), symbol,
                        Double.NEGATIVE_INFINITY, maxScore)
                .map(this::parseOrderId)
                .flatMapSequential(detailStore::findById);
    }

    @Override
    public Mono<Boolean> remove(Long orderId) {
        return detailStore.findById(orderId)
                .flatMap(order -> detailStore.delete(orderId)
                        .flatMap(deleted -> {
                            if (!deleted) {
                                return Mono.just(false);
                            }
                            String member = toMember(order);
                            return rankingStore.remove(sideKey(order.side()), order.symbol(), member)
                                    .thenReturn(true);
                        }))
                .defaultIfEmpty(false);
    }

    @Override
    public Mono<Void> updateQuantity(Order order, BigDecimal newQuantity) {
        return detailStore.updateQuantity(order.id(), newQuantity);
    }

    @Override
    public Mono<Boolean> isEmpty(String symbol, TradeSide side) {
        return rankingStore.isEmpty(sideKey(side), symbol);
    }

    // === 변환 로직 ===

    double toScore(Order order) {
        if (order.type() == OrderType.MARKET) {
            return order.side() == TradeSide.BUY ? BUY_MARKET_SCORE : SELL_MARKET_SCORE;
        }

        long scaledPrice = order.price()
                .multiply(BigDecimal.valueOf(priceScale))
                .longValueExact();

        return order.side() == TradeSide.BUY ? -scaledPrice : scaledPrice;
    }

    double toMaxScore(TradeSide side, BigDecimal matchPrice) {
        long scaledPrice = matchPrice.multiply(BigDecimal.valueOf(priceScale)).longValueExact();
        return side == TradeSide.BUY ? -scaledPrice : scaledPrice;
    }

    String toMember(Order order) {
        long epochMillis = order.createdAt().toInstant(ZoneOffset.UTC).toEpochMilli();
        return String.format(MEMBER_FORMAT, epochMillis, order.id());
    }

    Long parseOrderId(String member) {
        String[] parts = member.split("\\|");
        return Long.parseLong(parts[1]);
    }

    private String sideKey(TradeSide side) {
        return side == TradeSide.BUY ? "buy" : "sell";
    }
}
