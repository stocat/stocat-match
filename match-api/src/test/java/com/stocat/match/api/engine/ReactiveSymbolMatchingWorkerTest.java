package com.stocat.match.api.engine;

import com.stocat.match.api.infrastructure.trade.TradeApiClient;
import com.stocat.match.exception.ApiException;
import com.stocat.match.domain.TradeSide;
import com.stocat.match.domain.fill.Fill;
import com.stocat.match.domain.fill.FillResult;
import com.stocat.match.domain.order.Order;
import com.stocat.match.domain.order.OrderTif;
import com.stocat.match.domain.order.OrderType;
import com.stocat.match.domain.orderbook.Orderbook;
import com.stocat.match.domain.orderbook.PriceLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import reactor.core.scheduler.Schedulers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class ReactiveSymbolMatchingWorkerTest {

    @Mock
    private MatchingEngine matchingEngine;

    @Mock
    private TradeApiClient tradeApiClient;

    private ReactiveSymbolMatchingWorker worker;

    private static final String TEST_SYMBOL = "NVDA";

    @BeforeEach
    void setUp() {
        // Schedulers.immediate() 를 주입하여 동기방식으로 테스트
        worker = new ReactiveSymbolMatchingWorker(TEST_SYMBOL, matchingEngine, tradeApiClient, Schedulers.immediate());
    }

    @AfterEach
    void tearDown() {
        if (worker != null) {
            worker.shutdown();
        }
    }

    private Order createLimitBuyOrder(String symbol, BigDecimal quantity, BigDecimal price) {
        return new Order(
                1L,
                symbol,
                TradeSide.BUY,
                OrderType.LIMIT,
                quantity,
                price,
                OrderTif.GTC,
                null
        );
    }

    private Order createLimitSellOrder(String symbol, BigDecimal quantity, BigDecimal price) {
        return new Order(
                2L,
                symbol,
                TradeSide.SELL,
                OrderType.LIMIT,
                quantity,
                price,
                OrderTif.GTC,
                null
        );
    }

    private Orderbook createOrderbook(String symbol, List<PriceLevel> asks, List<PriceLevel> bids) {
        return new Orderbook(symbol, asks, bids);
    }

    private Fill createFill(String symbol, TradeSide side, BigDecimal price, BigDecimal quantity) {
        return new Fill(1L, symbol, side, price, quantity, LocalDateTime.now());
    }

    @Nested
    @DisplayName("주문 추가 시")
    class AddOrder {

        @Test
        void 정상적으로_주문이_추가된다() {
            // given
            Order order = createLimitBuyOrder(TEST_SYMBOL, BigDecimal.TEN, BigDecimal.valueOf(100));

            // when
            worker.addOrder(order);

            // then - 예외 없이 완료
        }

        @Test
        void 종목이_일치하지_않으면_예외가_발생한다() {
            // given
            Order order = createLimitBuyOrder("TSLA", BigDecimal.TEN, BigDecimal.valueOf(100));

            // when & then
            assertThatThrownBy(() -> worker.addOrder(order))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("종목 불일치");
        }

        @Test
        void 추가된_주문에_seq가_부여된다() {
            // given
            Order order1 = createLimitBuyOrder(TEST_SYMBOL, BigDecimal.TEN, BigDecimal.valueOf(100));
            Order order2 = createLimitBuyOrder(TEST_SYMBOL, BigDecimal.valueOf(20), BigDecimal.valueOf(100));

            Orderbook orderbook = createOrderbook(TEST_SYMBOL, List.of(), List.of());
            Fill fill = createFill(TEST_SYMBOL, TradeSide.BUY, BigDecimal.valueOf(100), BigDecimal.TEN);

            given(matchingEngine.match(any(Order.class), any(Orderbook.class)))
                    .willReturn(new FillResult(List.of(fill), BigDecimal.TEN, null));

            // when
            worker.addOrder(order1);
            worker.addOrder(order2);
            worker.processOrderbook(orderbook);

            // then
            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            then(matchingEngine).should(times(2))
                    .match(orderCaptor.capture(), any(Orderbook.class));

            List<Order> capturedOrders = orderCaptor.getAllValues();
            assertThat(capturedOrders.get(0).seq()).isEqualTo(0L);
            assertThat(capturedOrders.get(1).seq()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("호가 처리 시")
    class ProcessOrderbook {

        @Test
        void 정상적으로_호가가_처리된다() {
            // given
            Orderbook orderbook = createOrderbook(TEST_SYMBOL, List.of(), List.of());

            // when
            worker.processOrderbook(orderbook);

            // then - 예외 없이 완료
        }

        @Test
        void 종목이_일치하지_않으면_예외가_발생한다() {
            // given
            Orderbook orderbook = createOrderbook("TSLA", List.of(), List.of());

            // when & then
            assertThatThrownBy(() -> worker.processOrderbook(orderbook))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("종목 불일치");
        }
    }

    @Nested
    @DisplayName("체결 처리 시")
    class Matching {

        @Test
        void 매수주문이_매도호가와_체결되면_Fill이_발행된다() {
            // given
            Order buyOrder = createLimitBuyOrder(TEST_SYMBOL, BigDecimal.TEN, BigDecimal.valueOf(100));
            PriceLevel askLevel = new PriceLevel(BigDecimal.valueOf(100), BigDecimal.TEN);
            Orderbook orderbook = createOrderbook(TEST_SYMBOL, List.of(askLevel), List.of());

            Fill expectedFill = createFill(TEST_SYMBOL, TradeSide.BUY, BigDecimal.valueOf(100), BigDecimal.TEN);
            FillResult fillResult = new FillResult(List.of(expectedFill), BigDecimal.TEN, null);

            given(matchingEngine.match(any(Order.class), any(Orderbook.class)))
                    .willReturn(fillResult);

            // when
            worker.addOrder(buyOrder);
            worker.processOrderbook(orderbook);

            // then
            then(tradeApiClient).should()
                    .sendFill(any(Fill.class));
        }

        @Test
        void 여러_Fill이_발생하면_모두_개별_전송된다() {
            // given
            Order buyOrder = createLimitBuyOrder(TEST_SYMBOL, BigDecimal.valueOf(20), BigDecimal.valueOf(100));
            PriceLevel askLevel1 = new PriceLevel(BigDecimal.valueOf(99), BigDecimal.TEN);
            PriceLevel askLevel2 = new PriceLevel(BigDecimal.valueOf(100), BigDecimal.TEN);
            Orderbook orderbook = createOrderbook(TEST_SYMBOL, List.of(askLevel1, askLevel2), List.of());

            Fill fill1 = createFill(TEST_SYMBOL, TradeSide.BUY, BigDecimal.valueOf(99), BigDecimal.TEN);
            Fill fill2 = createFill(TEST_SYMBOL, TradeSide.BUY, BigDecimal.valueOf(100), BigDecimal.TEN);
            FillResult fillResult = new FillResult(List.of(fill1, fill2), BigDecimal.valueOf(20), null);

            given(matchingEngine.match(any(Order.class), any(Orderbook.class)))
                    .willReturn(fillResult);

            // when
            worker.addOrder(buyOrder);
            worker.processOrderbook(orderbook);

            // then
            then(tradeApiClient).should(times(2))
                    .sendFill(any(Fill.class));
        }

        @Test
        void 체결이_없으면_Fill이_발행되지_않는다() {
            // given
            Order buyOrder = createLimitBuyOrder(TEST_SYMBOL, BigDecimal.TEN, BigDecimal.valueOf(100));
            PriceLevel askLevel = new PriceLevel(BigDecimal.valueOf(101), BigDecimal.TEN);
            Orderbook orderbook = createOrderbook(TEST_SYMBOL, List.of(askLevel), List.of());

            given(matchingEngine.match(any(Order.class), any(Orderbook.class)))
                    .willReturn(new FillResult(List.of(), BigDecimal.ZERO, buyOrder));

            // when
            worker.addOrder(buyOrder);
            worker.processOrderbook(orderbook);

            // then
            then(tradeApiClient).should(never())
                    .sendFill(any(Fill.class));
        }
    }

    @Nested
    @DisplayName("부분 체결 시")
    class PartialFill {

        @Test
        void 부분_체결된_주문은_큐에_재등록된다() {
            // given
            Order buyOrder = createLimitBuyOrder(TEST_SYMBOL, BigDecimal.valueOf(20), BigDecimal.valueOf(100));
            PriceLevel askLevel = new PriceLevel(BigDecimal.valueOf(100), BigDecimal.TEN);
            Orderbook orderbook1 = createOrderbook(TEST_SYMBOL, List.of(askLevel), List.of());
            Orderbook orderbook2 = createOrderbook(TEST_SYMBOL, List.of(askLevel), List.of());

            Fill fill1 = createFill(TEST_SYMBOL, TradeSide.BUY, BigDecimal.valueOf(100), BigDecimal.TEN);
            Order remainingOrder = buyOrder.withQuantity(BigDecimal.TEN);
            FillResult fillResult1 = new FillResult(List.of(fill1), BigDecimal.TEN, remainingOrder);

            Fill fill2 = createFill(TEST_SYMBOL, TradeSide.BUY, BigDecimal.valueOf(100), BigDecimal.TEN);
            FillResult fillResult2 = new FillResult(List.of(fill2), BigDecimal.TEN, null);

            given(matchingEngine.match(any(Order.class), any(Orderbook.class)))
                    .willReturn(fillResult1)
                    .willReturn(fillResult2);

            // when
            worker.addOrder(buyOrder);
            worker.processOrderbook(orderbook1);
            worker.processOrderbook(orderbook2);

            // then - 총 2번의 Fill 발행 (첫 번째 부분 체결 + 두 번째 완전 체결)
            then(tradeApiClient).should(times(2))
                    .sendFill(any(Fill.class));
        }
    }

    @Nested
    @DisplayName("매수/매도 혼합 체결 시")
    class MixedSideMatching {

        @Test
        void 매수와_매도_주문이_모두_체결된다() {
            // given
            Order buyOrder = createLimitBuyOrder(TEST_SYMBOL, BigDecimal.TEN, BigDecimal.valueOf(100));
            Order sellOrder = createLimitSellOrder(TEST_SYMBOL, BigDecimal.TEN, BigDecimal.valueOf(99));

            PriceLevel askLevel = new PriceLevel(BigDecimal.valueOf(100), BigDecimal.TEN);
            PriceLevel bidLevel = new PriceLevel(BigDecimal.valueOf(99), BigDecimal.TEN);
            Orderbook orderbook = createOrderbook(TEST_SYMBOL, List.of(askLevel), List.of(bidLevel));

            Fill buyFill = createFill(TEST_SYMBOL, TradeSide.BUY, BigDecimal.valueOf(100), BigDecimal.TEN);
            Fill sellFill = createFill(TEST_SYMBOL, TradeSide.SELL, BigDecimal.valueOf(99), BigDecimal.TEN);
            FillResult buyFillResult = new FillResult(List.of(buyFill), BigDecimal.TEN, null);
            FillResult sellFillResult = new FillResult(List.of(sellFill), BigDecimal.TEN, null);

            given(matchingEngine.match(any(Order.class), any(Orderbook.class)))
                    .willReturn(buyFillResult)
                    .willReturn(sellFillResult);

            // when
            worker.addOrder(buyOrder);
            worker.addOrder(sellOrder);
            worker.processOrderbook(orderbook);

            // then - 매수 Fill 1개 + 매도 Fill 1개 = 총 2개
            then(tradeApiClient).should(times(2))
                    .sendFill(any(Fill.class));
        }
    }

    @Nested
    @DisplayName("종료 시")
    class Shutdown {

        @Test
        void 정상적으로_종료된다() {
            // when
            worker.shutdown();

            // then - 예외 없이 완료
        }
    }
}