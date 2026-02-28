package com.stocat.match.redis.order;

import com.stocat.match.domain.TradeSide;
import com.stocat.match.domain.order.Order;
import com.stocat.match.domain.order.OrderTif;
import com.stocat.match.domain.order.OrderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class RedisOrderRepositoryTest {

    @Mock
    private OrderZsetClient zsetClient;

    @Mock
    private OrderHashClient hashClient;

    @Mock
    private OrderLuaClient luaClient;

    private RedisOrderRepository repository;

    private static final long PRICE_SCALE = 10000;
    private static final String TEST_SYMBOL = "NVDA";
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2025, 1, 1, 9, 0, 0);

    @BeforeEach
    void setUp() {
        RedisOrderQueueProperties properties = new RedisOrderQueueProperties("order:", "order:detail:", PRICE_SCALE);
        repository = new RedisOrderRepository(zsetClient, hashClient, luaClient, properties);
    }

    private Order createLimitBuyOrder(Long id, BigDecimal quantity, BigDecimal price) {
        return new Order(id, TEST_SYMBOL, TradeSide.BUY, OrderType.LIMIT, quantity, price, OrderTif.GTC, CREATED_AT);
    }

    private Order createLimitSellOrder(Long id, BigDecimal quantity, BigDecimal price) {
        return new Order(id, TEST_SYMBOL, TradeSide.SELL, OrderType.LIMIT, quantity, price, OrderTif.GTC, CREATED_AT);
    }

    private Order createMarketBuyOrder(Long id, BigDecimal quantity) {
        return new Order(id, TEST_SYMBOL, TradeSide.BUY, OrderType.MARKET, quantity, null, OrderTif.GTC, CREATED_AT);
    }

    private Order createMarketSellOrder(Long id, BigDecimal quantity) {
        return new Order(id, TEST_SYMBOL, TradeSide.SELL, OrderType.MARKET, quantity, null, OrderTif.GTC, CREATED_AT);
    }

    @Nested
    @DisplayName("주문 추가 시")
    class AddOrder {

        @Test
        void Lua_스크립트로_ZSET과_HASH에_원자적으로_저장된다() {
            // given
            Order order = createLimitBuyOrder(1L, BigDecimal.TEN, BigDecimal.valueOf(1000));

            given(zsetClient.zsetKey("buy", TEST_SYMBOL)).willReturn("order:buy:NVDA");
            given(hashClient.hashKey(1L)).willReturn("order:detail:1");
            given(hashClient.toMap(order)).willReturn(Map.of("id", "1", "symbol", TEST_SYMBOL));
            given(luaClient.addOrder(eq("order:buy:NVDA"), eq("order:detail:1"),
                    anyDouble(), anyString(), anyMap()))
                    .willReturn(Mono.empty());

            // when & then
            StepVerifier.create(repository.addOrder(order))
                    .verifyComplete();

            then(luaClient).should().addOrder(eq("order:buy:NVDA"), eq("order:detail:1"),
                    anyDouble(), anyString(), anyMap());
        }
    }

    @Nested
    @DisplayName("체결 가능 주문 조회 시")
    class FetchMatchableOrders {

        @Test
        void score_범위_내_주문들이_반환된다() {
            // given
            BigDecimal matchPrice = BigDecimal.valueOf(1000);
            Order order1 = createLimitBuyOrder(1L, BigDecimal.TEN, BigDecimal.valueOf(1000));
            String member1 = repository.toMember(order1);

            given(zsetClient.rangeMembersByScore(eq("buy"), eq(TEST_SYMBOL),
                    eq(Double.NEGATIVE_INFINITY), anyDouble()))
                    .willReturn(Flux.just(member1));
            given(hashClient.findById(1L)).willReturn(Mono.just(order1));

            // when & then
            StepVerifier.create(repository.fetchMatchableOrders(TEST_SYMBOL, TradeSide.BUY, matchPrice))
                    .expectNext(order1)
                    .verifyComplete();
        }

        @Test
        void 조회_결과가_없으면_빈_Flux를_반환한다() {
            // given
            BigDecimal matchPrice = BigDecimal.valueOf(1000);

            given(zsetClient.rangeMembersByScore(eq("sell"), eq(TEST_SYMBOL),
                    eq(Double.NEGATIVE_INFINITY), anyDouble()))
                    .willReturn(Flux.empty());

            // when & then
            StepVerifier.create(repository.fetchMatchableOrders(TEST_SYMBOL, TradeSide.SELL, matchPrice))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("주문 제거 시")
    class Remove {

        @Test
        void Lua_스크립트로_Hash와_ZSET을_원자적으로_삭제하고_취소_수량을_반환한다() {
            // given
            Order order = createLimitBuyOrder(1L, BigDecimal.TEN, BigDecimal.valueOf(1000));
            String member = repository.toMember(order);

            given(hashClient.findById(1L)).willReturn(Mono.just(order));
            given(hashClient.hashKey(1L)).willReturn("order:detail:1");
            given(zsetClient.zsetKey("buy", TEST_SYMBOL)).willReturn("order:buy:NVDA");
            given(luaClient.remove("order:detail:1", "order:buy:NVDA", member))
                    .willReturn(Mono.just(BigDecimal.TEN));

            // when & then
            StepVerifier.create(repository.remove(1L))
                    .expectNext(BigDecimal.TEN)
                    .verifyComplete();
        }

        @Test
        void Hash가_이미_삭제된_경우_empty를_반환한다() {
            // given
            Order order = createLimitBuyOrder(1L, BigDecimal.TEN, BigDecimal.valueOf(1000));
            String member = repository.toMember(order);

            given(hashClient.findById(1L)).willReturn(Mono.just(order));
            given(hashClient.hashKey(1L)).willReturn("order:detail:1");
            given(zsetClient.zsetKey("buy", TEST_SYMBOL)).willReturn("order:buy:NVDA");
            given(luaClient.remove("order:detail:1", "order:buy:NVDA", member))
                    .willReturn(Mono.empty());

            // when & then
            StepVerifier.create(repository.remove(1L))
                    .verifyComplete();
        }

        @Test
        void 주문이_존재하지_않으면_empty를_반환한다() {
            // given
            given(hashClient.findById(999L)).willReturn(Mono.empty());

            // when & then
            StepVerifier.create(repository.remove(999L))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("수량 갱신 시")
    class UpdateQuantity {

        @Test
        void hashClient에_수량_갱신을_위임한다() {
            // given
            Order order = createLimitBuyOrder(1L, BigDecimal.TEN, BigDecimal.valueOf(1000));
            BigDecimal newQuantity = BigDecimal.valueOf(5);

            given(hashClient.updateQuantity(1L, newQuantity)).willReturn(Mono.empty());

            // when & then
            StepVerifier.create(repository.updateQuantity(order, newQuantity))
                    .verifyComplete();

            then(hashClient).should().updateQuantity(1L, newQuantity);
        }
    }

    @Nested
    @DisplayName("비어있는지 확인 시")
    class IsEmpty {

        @Test
        void zsetClient에_확인을_위임한다() {
            // given
            given(zsetClient.isEmpty("buy", TEST_SYMBOL)).willReturn(Mono.just(true));

            // when & then
            StepVerifier.create(repository.isEmpty(TEST_SYMBOL, TradeSide.BUY))
                    .expectNext(true)
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("score 변환 시")
    class ScoreConversion {

        @Test
        void 매수_지정가_주문은_음수_score로_변환된다() {
            // given
            Order order = createLimitBuyOrder(1L, BigDecimal.TEN, BigDecimal.valueOf(1000));

            // when
            double score = repository.toScore(order);

            // then
            assertThat(score).isEqualTo(-1000 * PRICE_SCALE);
        }

        @Test
        void 매도_지정가_주문은_양수_score로_변환된다() {
            // given
            Order order = createLimitSellOrder(1L, BigDecimal.TEN, BigDecimal.valueOf(1000));

            // when
            double score = repository.toScore(order);

            // then
            assertThat(score).isEqualTo(1000 * PRICE_SCALE);
        }

        @Test
        void 매수_시장가_주문은_음수_MAX_VALUE로_변환된다() {
            // given
            Order order = createMarketBuyOrder(1L, BigDecimal.TEN);

            // when
            double score = repository.toScore(order);

            // then
            assertThat(score).isEqualTo(-Double.MAX_VALUE);
        }

        @Test
        void 매도_시장가_주문은_음수1로_변환된다() {
            // given
            Order order = createMarketSellOrder(1L, BigDecimal.TEN);

            // when
            double score = repository.toScore(order);

            // then
            assertThat(score).isEqualTo(-1.0);
        }

        @Test
        void 매수_maxScore는_음수로_변환된다() {
            // when
            double maxScore = repository.toMaxScore(TradeSide.BUY, BigDecimal.valueOf(500));

            // then
            assertThat(maxScore).isEqualTo(-500 * PRICE_SCALE);
        }

        @Test
        void 매도_maxScore는_양수로_변환된다() {
            // when
            double maxScore = repository.toMaxScore(TradeSide.SELL, BigDecimal.valueOf(500));

            // then
            assertThat(maxScore).isEqualTo(500 * PRICE_SCALE);
        }
    }

    @Nested
    @DisplayName("member 변환 시")
    class MemberConversion {

        @Test
        void 올바른_형식의_member가_생성된다() {
            // given
            Order order = createLimitBuyOrder(42L, BigDecimal.TEN, BigDecimal.valueOf(1000));

            // when
            String member = repository.toMember(order);

            // then
            assertThat(member).matches("\\d{15}\\|42");
        }

        @Test
        void member에서_orderId가_파싱된다() {
            // given
            String member = "000001234567890|42";

            // when
            Long orderId = repository.parseOrderId(member);

            // then
            assertThat(orderId).isEqualTo(42L);
        }
    }
}