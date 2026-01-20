package com.stocat.match.redis.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocat.match.domain.orderbook.Orderbook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 호가 데이터 Redis Stream 소비자
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderbookStreamClient {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final RedisStreamProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * Consumer Group 생성 (기본 그룹명 사용)
     */
    public Mono<Void> createConsumerGroup(String symbol) {
        String streamKey = properties.getStreamKey(symbol);

        return redisTemplate.opsForStream()
                .createGroup(streamKey, ReadOffset.from("0"), properties.consumerGroupName())
                .onErrorResume(e -> {
                    if (e.getMessage() != null && e.getMessage().contains("BUSYGROUP")) {
                        return Mono.empty();
                    }
                    return Mono.error(e);
                })
                .then();
    }

    /**
     * Stream 구독
     */
    public Flux<OrderbookStreamMessage> subscribe(String symbol, String consumerName) {
        String streamKey = properties.getStreamKey(symbol);
        Consumer consumer = Consumer.from(properties.consumerGroupName(), consumerName);
        StreamReadOptions options = StreamReadOptions.empty()
                .block(properties.blockTimeout())
                .count(properties.batchSize());
        StreamOffset<String> streamOffset = StreamOffset.create(streamKey, ReadOffset.lastConsumed());

        return Flux.defer(() -> redisTemplate.opsForStream().read(consumer, options, streamOffset))
                .repeat()
                .flatMap(record -> convertToMessage(record, symbol))
                .doOnError(e -> log.error("Stream 소비 오류: symbol={}, error={}", symbol, e.getMessage()));
    }

    /**
     * 메시지 확인 (ACK)
     */
    public Mono<Long> acknowledge(String symbol, String recordId) {
        String streamKey = properties.getStreamKey(symbol);

        return redisTemplate.opsForStream()
                .acknowledge(streamKey, properties.consumerGroupName(), recordId);
    }

    /**
     * 처리되지 않은 메시지 조회 (Pending)
     */
    public Flux<OrderbookStreamMessage> readPending(String symbol, String groupName, String consumerName, long count) {
        String streamKey = properties.getStreamKey(symbol);
        Consumer consumer = Consumer.from(groupName, consumerName);
        StreamReadOptions options = StreamReadOptions.empty().count(count);

        return redisTemplate.opsForStream()
                .read(consumer, options, StreamOffset.create(streamKey, ReadOffset.from("0")))
                .flatMap(record -> convertToMessage(record, symbol));
    }

    @SuppressWarnings("unchecked")
    private Mono<OrderbookStreamMessage> convertToMessage(MapRecord<String, Object, Object> record, String symbol) {
        try {
            Map<String, String> value = (Map<String, String>) (Map<?, ?>) record.getValue();
            String dataJson = value.get("data");
            Orderbook orderbook = objectMapper.readValue(dataJson, Orderbook.class);

            return Mono.just(new OrderbookStreamMessage(
                    record.getId().getValue(),
                    symbol,
                    orderbook,
                    Long.parseLong(value.getOrDefault("timestamp", "0"))
            ));
        } catch (Exception e) {
            log.error("메시지 변환 실패: recordId={}, error={}", record.getId(), e.getMessage());
            return Mono.empty();
        }
    }
}