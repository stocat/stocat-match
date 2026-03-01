package com.stocat.match.redis.order;

import com.stocat.match.domain.TradeSide;
import com.stocat.match.domain.order.Order;
import com.stocat.match.domain.order.OrderTif;
import com.stocat.match.domain.order.OrderType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * HASH 기반 주문 상세 정보 저장/조회
 * - Order 도메인 객체의 직렬화/역직렬화 담당
 */
@Component
@RequiredArgsConstructor
public class OrderHashClient {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ReactiveStringRedisTemplate redisTemplate;
    private final RedisOrderQueueProperties properties;

    public Mono<Void> save(Order order) {
        String key = hashKey(order.id());
        Map<String, String> map = toMap(order);
        return redisTemplate.opsForHash()
                .putAll(key, map)
                .then();
    }

    public Mono<Order> findById(Long orderId) {
        String key = hashKey(orderId);
        return redisTemplate.<String, String>opsForHash()
                .entries(key)
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .filter(map -> !map.isEmpty())
                .map(this::fromMap);
    }

    public Mono<Boolean> delete(Long orderId) {
        String key = hashKey(orderId);
        return redisTemplate.delete(key)
                .map(count -> count > 0);
    }

    public Mono<Void> updateQuantity(Long orderId, BigDecimal newQuantity) {
        String key = hashKey(orderId);
        return redisTemplate.<String, String>opsForHash()
                .put(key, "quantity", newQuantity.toPlainString())
                .then();
    }

    Map<String, String> toMap(Order order) {
        Map<String, String> map = new HashMap<>();
        map.put("id", String.valueOf(order.id()));
        map.put("symbol", order.symbol());
        map.put("side", order.side().name());
        map.put("type", order.type().name());
        map.put("quantity", order.quantity().toPlainString());
        if (order.price() != null) {
            map.put("price", order.price().toPlainString());
        }
        map.put("tif", order.tif().name());
        if (order.createdAt() != null) {
            map.put("createdAt", order.createdAt().format(FORMATTER));
        }
        return map;
    }

    Order fromMap(Map<String, String> map) {
        return new Order(
                Long.parseLong(map.get("id")),
                map.get("symbol"),
                TradeSide.valueOf(map.get("side")),
                OrderType.valueOf(map.get("type")),
                new BigDecimal(map.get("quantity")),
                map.containsKey("price") ? new BigDecimal(map.get("price")) : null,
                OrderTif.valueOf(map.get("tif")),
                map.containsKey("createdAt") ? LocalDateTime.parse(map.get("createdAt"), FORMATTER) : null
        );
    }

    String hashKey(Long orderId) {
        return properties.detailKeyPrefix() + orderId;
    }
}