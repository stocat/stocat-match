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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class RedisOrderRepositoryTest {

    @Mock
    private OrderZsetClient zsetClient;

    @Mock
    private OrderHashClient hashClient;

    private RedisOrderRepository repository;

    private static final long PRICE_SCALE = 10000;
    private static final String TEST_SYMBOL = "NVDA";
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2025, 1, 1, 9, 0, 0);

    @BeforeEach
    void setUp() {
        RedisOrderQueueProperties properties = new RedisOrderQueueProperties("order:", "order:detail:", PRICE_SCALE);
        repository = new RedisOrderRepository(zsetClient, hashClient, properties);
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
        void ZSET과_HASH에_모두_저장된다() {
            // given
            Order order = createLimitBuyOrder(1L, BigDecimal.TEN, BigDecimal.valueOf(1000));

            given(zsetClient.add(anyString(), eq(TEST_SYMBOL), anyDouble(), anyString()))
                    .willReturn(Mono.just(true));
            given(hashClient.save(order)).willReturn(Mono.empty());

            // when & then
            StepVerifier.create(repository.addOrder(order))
                    .verifyComplete();

            then(zsetClient).should().add(eq("buy"), eq(TEST_SYMBOL), anyDouble(), anyString());
            then(hashClient).should().save(order);
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
        void Hash_삭제_성공_시_ZSET도_삭제하고_true를_반환한다() {
            // given
            Order order = createLimitBuyOrder(1L, BigDecimal.TEN, BigDecimal.valueOf(1000));
            String member = repository.toMember(order);

            given(hashClient.findById(1L)).willReturn(Mono.just(order));
            given(hashClient.delete(1L)).willReturn(Mono.just(true));
            given(zsetClient.remove("buy", TEST_SYMBOL, member)).willReturn(Mono.just(1L));

            // when & then
            StepVerifier.create(repository.remove(1L))
                    .expectNext(true)
                    .verifyComplete();

            then(zsetClient).should().remove("buy", TEST_SYMBOL, member);
        }

        @Test
        void Hash_삭제_실패_시_ZSET_삭제없이_false를_반환한다() {
            // given
            Order order = createLimitBuyOrder(1L, BigDecimal.TEN, BigDecimal.valueOf(1000));

            given(hashClient.findById(1L)).willReturn(Mono.just(order));
            given(hashClient.delete(1L)).willReturn(Mono.just(false));

            // when & then
            StepVerifier.create(repository.remove(1L))
                    .expectNext(false)
                    .verifyComplete();

            then(zsetClient).should(never()).remove(anyString(), anyString(), anyString());
        }

        @Test
        void 주문이_존재하지_않으면_false를_반환한다() {
            // given
            given(hashClient.findById(999L)).willReturn(Mono.empty());

            // when & then
            StepVerifier.create(repository.remove(999L))
                    .expectNext(false)
                    .verifyComplete();

            then(hashClient).should(never()).delete(999L);
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
