package com.stocat.match.api.engine;

import com.stocat.match.api.infrastructure.trade.TradeApiClient;
import com.stocat.match.domain.TradeSide;
import com.stocat.match.domain.fill.Fill;
import com.stocat.match.domain.order.Order;
import com.stocat.match.domain.order.OrderRepository;
import com.stocat.match.domain.order.OrderTif;
import com.stocat.match.domain.order.OrderType;
import com.stocat.match.domain.orderbook.Orderbook;
import com.stocat.match.domain.orderbook.PriceLevel;
import com.stocat.match.exception.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class ReactiveSymbolMatchingWorkerTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private MatchingEngine matchingEngine;

    @Mock
    private TradeApiClient tradeApiClient;

    private ReactiveSymbolMatchingWorker worker;

    private static final String TEST_SYMBOL = "NVDA";
    private static final LocalDateTime ORDER_TIME = LocalDateTime.of(2025, 1, 1, 9, 0, 0);
    private static final LocalDateTime ORDERBOOK_TIME = LocalDateTime.of(2025, 1, 1, 9, 0, 1);

    @BeforeEach
    void setUp() {
        worker = new ReactiveSymbolMatchingWorker(
                TEST_SYMBOL, orderRepository, matchingEngine, tradeApiClient, Schedulers.immediate());
    }

    @AfterEach
    void tearDown() {
        if (worker != null) {
            worker.shutdown();
        }
    }

    private Order createLimitBuyOrder(Long id, BigDecimal quantity, BigDecimal price) {
        return new Order(id, TEST_SYMBOL, TradeSide.BUY, OrderType.LIMIT, quantity, price, OrderTif.GTC, ORDER_TIME);
    }

    private Order createLimitSellOrder(Long id, BigDecimal quantity, BigDecimal price) {
        return new Order(id, TEST_SYMBOL, TradeSide.SELL, OrderType.LIMIT, quantity, price, OrderTif.GTC, ORDER_TIME);
    }

    private Orderbook createOrderbook(List<PriceLevel> asks, List<PriceLevel> bids) {
        return new Orderbook(TEST_SYMBOL, asks, bids, ORDERBOOK_TIME);
    }

    private Fill createFill(TradeSide side, BigDecimal price, BigDecimal quantity) {
        return new Fill(1L, TEST_SYMBOL, side, price, quantity, LocalDateTime.now());
    }

    @Nested
    @DisplayName("호가 처리 시")
    class ProcessOrderbook {

        @Test
        void 종목이_일치하지_않으면_에러가_발생한다() {
            // given
            Orderbook orderbook = new Orderbook("TSLA", List.of(), List.of(), ORDERBOOK_TIME);

            // when & then
            StepVerifier.create(worker.processOrderbook(orderbook))
                    .expectError(ApiException.class)
                    .verify();
        }

        @Test
        void 매도호가와_매수호가가_모두_비어있으면_체결없이_완료된다() {
            // given
            Orderbook orderbook = createOrderbook(List.of(), List.of());

            // when & then
            StepVerifier.create(worker.processOrderbook(orderbook))
                    .verifyComplete();

            then(matchingEngine).should(never()).match(any(), any());
        }

        @Test
        void 체결_가능한_주문이_없으면_체결없이_완료된다() {
            // given
            BigDecimal askPrice = BigDecimal.valueOf(1000);
            Orderbook orderbook = createOrderbook(
                    List.of(new PriceLevel(askPrice, BigDecimal.TEN)), List.of());

            given(orderRepository.fetchMatchableOrders(TEST_SYMBOL, TradeSide.BUY, askPrice))
                    .willReturn(Flux.empty());

            // when & then
            StepVerifier.create(worker.processOrderbook(orderbook))
                    .verifyComplete();

            then(matchingEngine).should(never()).match(any(), any());
        }
    }

    @Nested
    @DisplayName("매수주문 체결 시")
    class BuyOrderMatching {

        @Test
        void 완전_체결되면_주문이_제거되고_Fill이_발행된다() {
            // given
            BigDecimal askPrice = BigDecimal.valueOf(1000);
            BigDecimal quantity = BigDecimal.TEN;
            Order buyOrder = createLimitBuyOrder(1L, quantity, BigDecimal.valueOf(1500));

            Orderbook orderbook = createOrderbook(
                    List.of(new PriceLevel(askPrice, quantity)), List.of());

            Fill fill = createFill(TradeSide.BUY, askPrice, quantity);
            MatchResult filledResult = MatchResult.filled(List.of(fill), BigDecimal.ZERO);

            given(orderRepository.fetchMatchableOrders(TEST_SYMBOL, TradeSide.BUY, askPrice))
                    .willReturn(Flux.just(buyOrder));
            given(matchingEngine.match(buyOrder, orderbook)).willReturn(filledResult);
            given(orderRepository.remove(1L)).willReturn(Mono.just(true));

            // when & then
            StepVerifier.create(worker.processOrderbook(orderbook))
                    .verifyComplete();

            then(orderRepository).should().remove(1L);
            then(tradeApiClient).should().sendFill(fill);
        }

        @Test
        void 부분_체결되면_수량이_갱신되고_Fill이_발행된다() {
            // given
            BigDecimal askPrice = BigDecimal.valueOf(1000);
            BigDecimal orderQuantity = BigDecimal.valueOf(20);
            BigDecimal remainingQuantity = BigDecimal.TEN;
            Order buyOrder = createLimitBuyOrder(1L, orderQuantity, BigDecimal.valueOf(1500));

            Orderbook orderbook = createOrderbook(
                    List.of(new PriceLevel(askPrice, BigDecimal.valueOf(10))), List.of());

            Fill fill = createFill(TradeSide.BUY, askPrice, BigDecimal.TEN);
            MatchResult partialResult = MatchResult.filled(List.of(fill), remainingQuantity);

            given(orderRepository.fetchMatchableOrders(TEST_SYMBOL, TradeSide.BUY, askPrice))
                    .willReturn(Flux.just(buyOrder));
            given(matchingEngine.match(buyOrder, orderbook)).willReturn(partialResult);
            given(orderRepository.updateQuantity(buyOrder, remainingQuantity)).willReturn(Mono.empty());

            // when & then
            StepVerifier.create(worker.processOrderbook(orderbook))
                    .verifyComplete();

            then(orderRepository).should().updateQuantity(buyOrder, remainingQuantity);
            then(orderRepository).should(never()).remove(any());
            then(tradeApiClient).should().sendFill(fill);
        }

        @Test
        void STOP이면_이후_주문은_처리하지_않는다() {
            // given
            BigDecimal askPrice = BigDecimal.valueOf(1000);
            Order order1 = createLimitBuyOrder(1L, BigDecimal.TEN, BigDecimal.valueOf(500));
            Order order2 = createLimitBuyOrder(2L, BigDecimal.TEN, BigDecimal.valueOf(500));

            Orderbook orderbook = createOrderbook(
                    List.of(new PriceLevel(askPrice, BigDecimal.valueOf(100))), List.of());

            MatchResult stopResult = MatchResult.stop(BigDecimal.TEN);

            given(orderRepository.fetchMatchableOrders(TEST_SYMBOL, TradeSide.BUY, askPrice))
                    .willReturn(Flux.just(order1, order2));
            given(matchingEngine.match(order1, orderbook)).willReturn(stopResult);

            // when & then
            StepVerifier.create(worker.processOrderbook(orderbook))
                    .verifyComplete();

            then(matchingEngine).should(times(1)).match(any(), any());
            then(tradeApiClient).should(never()).sendFill(any());
        }

        @Test
        void SKIP이면_다음_주문을_계속_처리한다() {
            // given
            BigDecimal askPrice = BigDecimal.valueOf(1000);
            Order order1 = createLimitBuyOrder(1L, BigDecimal.TEN, BigDecimal.valueOf(1500));
            Order order2 = createLimitBuyOrder(2L, BigDecimal.TEN, BigDecimal.valueOf(1500));

            Orderbook orderbook = createOrderbook(
                    List.of(new PriceLevel(askPrice, BigDecimal.valueOf(100))), List.of());

            MatchResult skipResult = MatchResult.skip(BigDecimal.TEN);
            Fill fill = createFill(TradeSide.BUY, askPrice, BigDecimal.TEN);
            MatchResult filledResult = MatchResult.filled(List.of(fill), BigDecimal.ZERO);

            given(orderRepository.fetchMatchableOrders(TEST_SYMBOL, TradeSide.BUY, askPrice))
                    .willReturn(Flux.just(order1, order2));
            given(matchingEngine.match(order1, orderbook)).willReturn(skipResult);
            given(matchingEngine.match(order2, orderbook)).willReturn(filledResult);
            given(orderRepository.remove(2L)).willReturn(Mono.just(true));

            // when & then
            StepVerifier.create(worker.processOrderbook(orderbook))
                    .verifyComplete();

            then(matchingEngine).should(times(2)).match(any(), any());
            then(tradeApiClient).should().sendFill(fill);
        }

        @Test
        void 완전_체결_시_remove가_false면_취소된_주문으로_SKIP_처리된다() {
            // given
            BigDecimal askPrice = BigDecimal.valueOf(1000);
            Order buyOrder = createLimitBuyOrder(1L, BigDecimal.TEN, BigDecimal.valueOf(1500));

            Orderbook orderbook = createOrderbook(
                    List.of(new PriceLevel(askPrice, BigDecimal.TEN)), List.of());

            Fill fill = createFill(TradeSide.BUY, askPrice, BigDecimal.TEN);
            MatchResult filledResult = MatchResult.filled(List.of(fill), BigDecimal.ZERO);

            given(orderRepository.fetchMatchableOrders(TEST_SYMBOL, TradeSide.BUY, askPrice))
                    .willReturn(Flux.just(buyOrder));
            given(matchingEngine.match(buyOrder, orderbook)).willReturn(filledResult);
            given(orderRepository.remove(1L)).willReturn(Mono.just(false));

            // when & then
            StepVerifier.create(worker.processOrderbook(orderbook))
                    .verifyComplete();

            then(tradeApiClient).should(never()).sendFill(any());
        }
    }

    @Nested
    @DisplayName("매수/매도 동시 체결 시")
    class MixedSideMatching {

        @Test
        void 매수와_매도_주문이_모두_체결된다() {
            // given
            BigDecimal askPrice = BigDecimal.valueOf(1000);
            BigDecimal bidPrice = BigDecimal.valueOf(900);
            Order buyOrder = createLimitBuyOrder(1L, BigDecimal.TEN, BigDecimal.valueOf(1500));
            Order sellOrder = createLimitSellOrder(2L, BigDecimal.TEN, BigDecimal.valueOf(800));

            Orderbook orderbook = createOrderbook(
                    List.of(new PriceLevel(askPrice, BigDecimal.TEN)),
                    List.of(new PriceLevel(bidPrice, BigDecimal.TEN)));

            Fill buyFill = createFill(TradeSide.BUY, askPrice, BigDecimal.TEN);
            Fill sellFill = createFill(TradeSide.SELL, bidPrice, BigDecimal.TEN);
            MatchResult buyResult = MatchResult.filled(List.of(buyFill), BigDecimal.ZERO);
            MatchResult sellResult = MatchResult.filled(List.of(sellFill), BigDecimal.ZERO);

            given(orderRepository.fetchMatchableOrders(TEST_SYMBOL, TradeSide.BUY, askPrice))
                    .willReturn(Flux.just(buyOrder));
            given(orderRepository.fetchMatchableOrders(TEST_SYMBOL, TradeSide.SELL, bidPrice))
                    .willReturn(Flux.just(sellOrder));
            given(matchingEngine.match(buyOrder, orderbook)).willReturn(buyResult);
            given(matchingEngine.match(sellOrder, orderbook)).willReturn(sellResult);
            given(orderRepository.remove(1L)).willReturn(Mono.just(true));
            given(orderRepository.remove(2L)).willReturn(Mono.just(true));

            // when & then
            StepVerifier.create(worker.processOrderbook(orderbook))
                    .verifyComplete();

            then(tradeApiClient).should(times(2)).sendFill(any());
        }
    }

    @Nested
    @DisplayName("종료 시")
    class Shutdown {

        @Test
        void 정상적으로_종료된다() {
            // when & then
            worker.shutdown();
            assertThat(worker.getSymbol()).isEqualTo(TEST_SYMBOL);
        }
    }
}
