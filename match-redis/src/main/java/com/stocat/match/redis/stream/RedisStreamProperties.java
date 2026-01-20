package com.stocat.match.redis.stream;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "redis.stream.orderbook")
public record RedisStreamProperties(
        @DefaultValue("orderbook:stream:") String keyPrefix,
        @DefaultValue("matching-engine") String consumerGroupName,
        @DefaultValue("5s") Duration blockTimeout,
        @DefaultValue("10") int batchSize
) {
    public String getStreamKey(String symbol) {
        return keyPrefix + symbol;
    }
}