package com.stocat.match.redis.order;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "redis.order-queue")
public record RedisOrderQueueProperties(
        @DefaultValue("order:") String keyPrefix,
        @DefaultValue("order:detail:") String detailKeyPrefix,
        @DefaultValue("10000") long priceScale
) {
}