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

    private final OrderZsetClient zsetClient;
    private final OrderHashClient hashClient;
    private final long priceScale;

    public RedisOrderRepository(
            OrderZsetClient zsetClient,
            OrderHashClient hashClient,
            RedisOrderQueueProperties orderQueueProperties) {
        this.zsetClient = zsetClient;
        this.hashClient = hashClient;
        this.priceScale = orderQueueProperties.priceScale();
    }

    @Override
    public Mono<Void> addOrder(Order order) {
        double score = toScore(order);
        String member = toMember(order);
        String side = sideKey(order.side());

        return zsetClient.add(side, order.symbol(), score, member)
                .then(hashClient.save(order));
    }

    /**
     * 체결 가능 주문을 우선순위 순으로 일괄 조회한다.
     * 1. ZRANGEBYSCORE(-∞, maxScore)로 score 범위 내 member 조회
     * 2. member에서 orderId 파싱 → HASH에서 주문 상세 조회
     */
    @Override
    public Flux<Order> fetchMatchableOrders(String symbol, TradeSide side, BigDecimal matchPrice) {
        double maxScore = toMaxScore(side, matchPrice);
        return zsetClient.rangeMembersByScore(sideKey(side), symbol,
                        Double.NEGATIVE_INFINITY, maxScore)
                .map(this::parseOrderId)
                .flatMapSequential(hashClient::findById);
    }

    /**
     * 주문을 제거한다. Hash 삭제를 CAS로 활용하여 취소 경합을 감지한다.
     * - Hash 삭제 성공 → ZSET 삭제 → true
     * - Hash 삭제 실패(이미 취소됨) → false
     */
    @Override
    public Mono<Boolean> remove(Long orderId) {
        return hashClient.findById(orderId)
                .flatMap(order -> hashClient.delete(orderId)
                        .filter(deleted -> deleted)
                        .flatMap(deleted -> {
                            String member = toMember(order);
                            return zsetClient.remove(sideKey(order.side()), order.symbol(), member);
                        })
                        .hasElement())
                .defaultIfEmpty(false);
    }

    @Override
    public Mono<Void> updateQuantity(Order order, BigDecimal newQuantity) {
        return hashClient.updateQuantity(order.id(), newQuantity);
    }

    @Override
    public Mono<Boolean> isEmpty(String symbol, TradeSide side) {
        return zsetClient.isEmpty(sideKey(side), symbol);
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
