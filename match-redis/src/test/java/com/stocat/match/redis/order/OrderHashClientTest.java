package com.stocat.match.redis.order;

import com.stocat.match.domain.TradeSide;
import com.stocat.match.domain.order.Order;
import com.stocat.match.domain.order.OrderTif;
import com.stocat.match.domain.order.OrderType;
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
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class OrderHashClientTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    private OrderHashClient client;
    private ReactiveStringRedisTemplate redisTemplate;

    private static final String DETAIL_KEY_PREFIX = "order:detail:";
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2025, 1, 1, 9, 0, 0);

    @BeforeEach
    void setUp() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                redis.getHost(), redis.getMappedPort(6379));
        factory.afterPropertiesSet();

        redisTemplate = new ReactiveStringRedisTemplate(factory);
        RedisOrderQueueProperties properties = new RedisOrderQueueProperties("order:", DETAIL_KEY_PREFIX, 10000);
        client = new OrderHashClient(redisTemplate, properties);

        redisTemplate.execute(connection -> connection.serverCommands().flushAll()).blockLast();
    }

    private Order createLimitBuyOrder() {
        return new Order(1L, "NVDA", TradeSide.BUY, OrderType.LIMIT,
                BigDecimal.TEN, BigDecimal.valueOf(1000), OrderTif.GTC, CREATED_AT);
    }

    private Order createMarketSellOrder() {
        return new Order(2L, "NVDA", TradeSide.SELL, OrderType.MARKET,
                BigDecimal.valueOf(5), null, OrderTif.GTC, CREATED_AT);
    }

    @Nested
    @DisplayName("주문 저장 시")
    class Save {

        @Test
        void HASH에_주문_정보가_저장된다() {
            // given
            Order order = createLimitBuyOrder();

            // when & then
            StepVerifier.create(client.save(order))
                    .verifyComplete();

            // 저장 확인
            StepVerifier.create(client.findById(order.id()))
                    .assertNext(found -> {
                        assertThat(found.id()).isEqualTo(order.id());
                        assertThat(found.symbol()).isEqualTo(order.symbol());
                        assertThat(found.side()).isEqualTo(order.side());
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("주문 조회 시")
    class FindById {

        @Test
        void 저장된_지정가_주문이_정확히_복원된다() {
            // given
            Order order = createLimitBuyOrder();
            client.save(order).block();

            // when & then
            StepVerifier.create(client.findById(order.id()))
                    .assertNext(found -> {
                        assertThat(found.id()).isEqualTo(1L);
                        assertThat(found.symbol()).isEqualTo("NVDA");
                        assertThat(found.side()).isEqualTo(TradeSide.BUY);
                        assertThat(found.type()).isEqualTo(OrderType.LIMIT);
                        assertThat(found.quantity()).isEqualByComparingTo(BigDecimal.TEN);
                        assertThat(found.price()).isEqualByComparingTo(BigDecimal.valueOf(1000));
                        assertThat(found.tif()).isEqualTo(OrderTif.GTC);
                        assertThat(found.createdAt()).isEqualTo(CREATED_AT);
                    })
                    .verifyComplete();
        }

        @Test
        void 시장가_주문은_price가_null로_복원된다() {
            // given
            Order order = createMarketSellOrder();
            client.save(order).block();

            // when & then
            StepVerifier.create(client.findById(order.id()))
                    .assertNext(found -> {
                        assertThat(found.price()).isNull();
                        assertThat(found.type()).isEqualTo(OrderType.MARKET);
                        assertThat(found.side()).isEqualTo(TradeSide.SELL);
                    })
                    .verifyComplete();
        }

        @Test
        void 존재하지_않는_주문은_빈_Mono를_반환한다() {
            // when & then
            StepVerifier.create(client.findById(999L))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("주문 삭제 시")
    class Delete {

        @Test
        void 존재하는_주문이_삭제되면_true를_반환한다() {
            // given
            Order order = createLimitBuyOrder();
            client.save(order).block();

            // when & then
            StepVerifier.create(client.delete(order.id()))
                    .expectNext(true)
                    .verifyComplete();

            // 삭제 확인
            StepVerifier.create(client.findById(order.id()))
                    .verifyComplete();
        }

        @Test
        void 존재하지_않는_주문_삭제_시_false를_반환한다() {
            // when & then
            StepVerifier.create(client.delete(999L))
                    .expectNext(false)
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("수량 갱신 시")
    class UpdateQuantity {

        @Test
        void 수량이_정상적으로_갱신된다() {
            // given
            Order order = createLimitBuyOrder();
            client.save(order).block();
            BigDecimal newQuantity = BigDecimal.valueOf(5);

            // when
            client.updateQuantity(order.id(), newQuantity).block();

            // then
            StepVerifier.create(client.findById(order.id()))
                    .assertNext(found ->
                            assertThat(found.quantity()).isEqualByComparingTo(newQuantity))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("직렬화/역직렬화 시")
    class Serialization {

        @Test
        void 지정가_주문이_올바르게_변환된다() {
            // given
            Order order = createLimitBuyOrder();

            // when
            Map<String, String> map = client.toMap(order);
            Order restored = client.fromMap(map);

            // then
            assertThat(restored.id()).isEqualTo(order.id());
            assertThat(restored.symbol()).isEqualTo(order.symbol());
            assertThat(restored.side()).isEqualTo(order.side());
            assertThat(restored.type()).isEqualTo(order.type());
            assertThat(restored.quantity()).isEqualByComparingTo(order.quantity());
            assertThat(restored.price()).isEqualByComparingTo(order.price());
            assertThat(restored.tif()).isEqualTo(order.tif());
            assertThat(restored.createdAt()).isEqualTo(order.createdAt());
        }

        @Test
        void 시장가_주문은_price_없이_변환된다() {
            // given
            Order order = createMarketSellOrder();

            // when
            Map<String, String> map = client.toMap(order);
            Order restored = client.fromMap(map);

            // then
            assertThat(restored.price()).isNull();
            assertThat(map).doesNotContainKey("price");
        }

        @Test
        void 키가_올바른_형식으로_생성된다() {
            // when & then
            assertThat(client.hashKey(1L)).isEqualTo("order:detail:1");
            assertThat(client.hashKey(999L)).isEqualTo("order:detail:999");
        }
    }
}
