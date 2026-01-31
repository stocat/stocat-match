package com.stocat.match.redis.stream;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RedisStreamProperties.class)
public class RedisStreamConfig {
}