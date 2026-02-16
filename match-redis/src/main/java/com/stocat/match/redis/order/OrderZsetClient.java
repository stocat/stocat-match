package com.stocat.match.redis.order;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * ZSET 기반 주문 정렬/우선순위 관리
 * - score/member 수준의 저수준 연산만 담당
 * - Order 도메인 객체를 알지 않음
 */
@Component
@RequiredArgsConstructor
public class OrderZsetClient {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final RedisOrderQueueProperties properties;

    public Mono<Boolean> add(String side, String symbol, double score, String member) {
        String key = zsetKey(side, symbol);
        return redisTemplate.opsForZSet().add(key, member, score);
    }

    public Flux<String> rangeMembersByScore(String side, String symbol,
                                            double minScore, double maxScore) {
        String key = zsetKey(side, symbol);
        return redisTemplate.opsForZSet()
                .rangeByScore(key, org.springframework.data.domain.Range.closed(minScore, maxScore))
                .map(String::valueOf);
    }

    public Mono<Long> remove(String side, String symbol, String member) {
        String key = zsetKey(side, symbol);
        return redisTemplate.opsForZSet().remove(key, member);
    }

    public Mono<Boolean> isEmpty(String side, String symbol) {
        String key = zsetKey(side, symbol);
        return redisTemplate.opsForZSet()
                .size(key)
                .map(count -> count == 0);
    }

    String zsetKey(String side, String symbol) {
        return properties.keyPrefix() + side + ":" + symbol;
    }
}