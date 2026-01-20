package com.stocat.match.redis.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocat.match.domain.orderbook.Orderbook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 호가 데이터 Redis Stream 발행자
 */
@Slf4j
@Component
public class OrderbookStreamPublisher {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final RedisStreamProperties properties;
    private final ObjectMapper objectMapper;

    public OrderbookStreamPublisher(
            ReactiveStringRedisTemplate redisTemplate,
            RedisStreamProperties properties,
            ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 호가 데이터를 Stream에 발행
     */
    public Mono<RecordId> publish(Orderbook orderbook) {
        String streamKey = properties.getStreamKey(orderbook.symbol());

        return Mono.fromCallable(() -> objectMapper.writeValueAsString(orderbook))
                .map(json -> StreamRecords.newRecord()
                        .in(streamKey)
                        .ofObject(Map.of(
                                "symbol", orderbook.symbol(),
                                "data", json,
                                "timestamp", String.valueOf(System.currentTimeMillis())
                        )))
                .flatMap(record -> redisTemplate.opsForStream().add(record))
                .doOnError(e -> log.error("Stream 발행 실패: symbol={}, error={}", orderbook.symbol(), e.getMessage()));
    }
}