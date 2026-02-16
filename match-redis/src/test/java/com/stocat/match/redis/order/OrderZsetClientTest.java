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

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class OrderZsetClientTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    private OrderZsetClient client;
    private ReactiveStringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "order:";
    private static final String TEST_SYMBOL = "NVDA";

    @BeforeEach
    void setUp() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                redis.getHost(), redis.getMappedPort(6379));
        factory.afterPropertiesSet();

        redisTemplate = new ReactiveStringRedisTemplate(factory);
        RedisOrderQueueProperties properties = new RedisOrderQueueProperties(KEY_PREFIX, "order:detail:", 10000);
        client = new OrderZsetClient(redisTemplate, properties);

        // 테스트 간 데이터 격리
        redisTemplate.execute(connection -> connection.serverCommands().flushAll()).blockLast();
    }

    @Nested
    @DisplayName("주문 추가 시")
    class Add {

        @Test
        void ZSET에_score와_member가_추가된다() {
            // given
            double score = -1000000.0;
            String member = "000001234567890|1";

            // when & then
            StepVerifier.create(client.add("buy", TEST_SYMBOL, score, member))
                    .expectNext(true)
                    .verifyComplete();

            // ZSET에 실제로 저장되었는지 확인
            StepVerifier.create(client.isEmpty("buy", TEST_SYMBOL))
                    .expectNext(false)
                    .verifyComplete();
        }

        @Test
        void 동일한_member를_중복_추가하면_false를_반환한다() {
            // given
            String member = "000001234567890|1";
            client.add("buy", TEST_SYMBOL, -1000000.0, member).block();

            // when & then
            StepVerifier.create(client.add("buy", TEST_SYMBOL, -1000000.0, member))
                    .expectNext(false)
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("score 범위 조회 시")
    class RangeMembersByScore {

        @Test
        void 범위_내_member들이_score_순서로_반환된다() {
            // given
            String member1 = "000001234567890|1";
            String member2 = "000001234567891|2";
            String member3 = "000001234567892|3";

            client.add("buy", TEST_SYMBOL, -2000000.0, member1).block();  // 높은 우선순위
            client.add("buy", TEST_SYMBOL, -1000000.0, member2).block();
            client.add("buy", TEST_SYMBOL, -500000.0, member3).block();   // 낮은 우선순위

            // when & then — maxScore=-1000000이면 score <= -1000000인 member만
            StepVerifier.create(client.rangeMembersByScore("buy", TEST_SYMBOL,
                            Double.NEGATIVE_INFINITY, -1000000.0))
                    .expectNext(member1)
                    .expectNext(member2)
                    .verifyComplete();
        }

        @Test
        void 범위_내_member가_없으면_빈_Flux를_반환한다() {
            // when & then
            StepVerifier.create(client.rangeMembersByScore("sell", TEST_SYMBOL,
                            Double.NEGATIVE_INFINITY, 500000.0))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("주문 제거 시")
    class Remove {

        @Test
        void member가_정상적으로_제거된다() {
            // given
            String member = "000001234567890|1";
            client.add("buy", TEST_SYMBOL, -1000000.0, member).block();

            // when & then
            StepVerifier.create(client.remove("buy", TEST_SYMBOL, member))
                    .expectNext(1L)
                    .verifyComplete();

            StepVerifier.create(client.isEmpty("buy", TEST_SYMBOL))
                    .expectNext(true)
                    .verifyComplete();
        }

        @Test
        void 존재하지_않는_member_제거_시_0을_반환한다() {
            // when & then
            StepVerifier.create(client.remove("buy", TEST_SYMBOL, "nonexistent|999"))
                    .expectNext(0L)
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("비어있는지 확인 시")
    class IsEmpty {

        @Test
        void ZSET이_비어있으면_true를_반환한다() {
            // when & then
            StepVerifier.create(client.isEmpty("buy", TEST_SYMBOL))
                    .expectNext(true)
                    .verifyComplete();
        }

        @Test
        void ZSET에_데이터가_있으면_false를_반환한다() {
            // given
            client.add("sell", TEST_SYMBOL, 1000000.0, "000001234567890|1").block();

            // when & then
            StepVerifier.create(client.isEmpty("sell", TEST_SYMBOL))
                    .expectNext(false)
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("키 생성 시")
    class ZsetKey {

        @Test
        void 올바른_형식의_키가_생성된다() {
            // when & then
            assertThat(client.zsetKey("buy", "NVDA")).isEqualTo("order:buy:NVDA");
            assertThat(client.zsetKey("sell", "TSLA")).isEqualTo("order:sell:TSLA");
        }
    }
}
