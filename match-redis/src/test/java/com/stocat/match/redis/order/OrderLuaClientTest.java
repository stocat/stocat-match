package com.stocat.match.redis.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class OrderLuaClientTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    private OrderLuaClient luaClient;
    private ReactiveStringRedisTemplate redisTemplate;

    private static final String ZSET_KEY = "order:buy:NVDA";
    private static final String HASH_KEY = "order:detail:1";
    private static final String MEMBER = "000001735689600000|1";
    private static final double SCORE = -10000000.0;

    @BeforeEach
    void setUp() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                redis.getHost(), redis.getMappedPort(6379));
        factory.afterPropertiesSet();

        redisTemplate = new ReactiveStringRedisTemplate(factory);
        luaClient = new OrderLuaClient(redisTemplate);

        redisTemplate.execute(connection -> connection.serverCommands().flushAll()).blockLast();
    }

    @Nested
    @DisplayName("주문 추가 시")
    class AddOrder {

        @Test
        void ZSET과_HASH에_원자적으로_저장된다() {
            // given
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("id", "1");
            fields.put("symbol", "NVDA");
            fields.put("side", "BUY");
            fields.put("type", "LIMIT");
            fields.put("quantity", "10");
            fields.put("price", "1000");
            fields.put("tif", "GTC");
            fields.put("createdAt", "2025-01-01T09:00:00");

            // when
            StepVerifier.create(luaClient.addOrder(ZSET_KEY, HASH_KEY, SCORE, MEMBER, fields))
                    .verifyComplete();

            // then — ZSET에 member가 존재하는지 확인
            StepVerifier.create(redisTemplate.opsForZSet().size(ZSET_KEY))
                    .assertNext(size -> assertThat(size).isEqualTo(1))
                    .verifyComplete();

            // then — HASH에 필드가 존재하는지 확인
            StepVerifier.create(redisTemplate.<String, String>opsForHash()
                            .entries(HASH_KEY)
                            .collectMap(Map.Entry::getKey, Map.Entry::getValue))
                    .assertNext(map -> {
                        assertThat(map).containsEntry("id", "1");
                        assertThat(map).containsEntry("symbol", "NVDA");
                        assertThat(map).containsEntry("side", "BUY");
                        assertThat(map).containsEntry("price", "1000");
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("주문 삭제 시")
    class Remove {

        @Test
        void Hash와_ZSET이_원자적으로_삭제되고_취소_수량을_반환한다() {
            // given — 먼저 주문 추가
            Map<String, String> fields = Map.of("id", "1", "symbol", "NVDA", "side", "BUY", "quantity", "10");
            luaClient.addOrder(ZSET_KEY, HASH_KEY, SCORE, MEMBER, fields).block();

            // when & then
            StepVerifier.create(luaClient.remove(HASH_KEY, ZSET_KEY, MEMBER))
                    .assertNext(quantity -> assertThat(quantity).isEqualByComparingTo(BigDecimal.TEN))
                    .verifyComplete();

            // then — HASH 삭제 확인
            StepVerifier.create(redisTemplate.hasKey(HASH_KEY))
                    .expectNext(false)
                    .verifyComplete();

            // then — ZSET에서 member 제거 확인
            StepVerifier.create(redisTemplate.opsForZSet().size(ZSET_KEY))
                    .assertNext(size -> assertThat(size).isEqualTo(0))
                    .verifyComplete();
        }

        @Test
        void 이미_삭제된_주문이면_empty를_반환한다() {
            // given — 주문이 없는 상태

            // when & then
            StepVerifier.create(luaClient.remove(HASH_KEY, ZSET_KEY, MEMBER))
                    .verifyComplete();
        }

        @Test
        void 동시_삭제_시_하나만_성공한다() {
            // given
            Map<String, String> fields = Map.of("id", "1", "symbol", "NVDA", "side", "BUY", "quantity", "10");
            luaClient.addOrder(ZSET_KEY, HASH_KEY, SCORE, MEMBER, fields).block();

            // when — 두 번 연속 삭제 시도
            BigDecimal first = luaClient.remove(HASH_KEY, ZSET_KEY, MEMBER).block();
            BigDecimal second = luaClient.remove(HASH_KEY, ZSET_KEY, MEMBER).block();

            // then
            assertThat(first).isEqualByComparingTo(BigDecimal.TEN);
            assertThat(second).isNull();
        }
    }
}