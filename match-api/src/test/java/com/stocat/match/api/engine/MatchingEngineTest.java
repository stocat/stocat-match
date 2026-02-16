package com.stocat.match.api.engine;

import com.stocat.match.domain.TradeSide;
import com.stocat.match.domain.fill.Fill;
import com.stocat.match.domain.order.Order;
import com.stocat.match.domain.order.OrderTif;
import com.stocat.match.domain.order.OrderType;
import com.stocat.match.domain.orderbook.Orderbook;
import com.stocat.match.domain.orderbook.PriceLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MatchingEngineTest {
    private MatchingEngine matchingEngine;

    public static final String TEST_SYMBOL = "NVDA";
    private static final LocalDateTime ORDER_TIME = LocalDateTime.of(2025, 1, 1, 9, 0, 0);
    private static final LocalDateTime ORDERBOOK_TIME = LocalDateTime.of(2025, 1, 1, 9, 0, 1);

    @BeforeEach
    void setUp() {
        matchingEngine = new MatchingEngine();
    }

    private Order createLimitBuyOrder(String symbol, BigDecimal quantity, BigDecimal price) {
        return new Order(1L, symbol, TradeSide.BUY, OrderType.LIMIT, quantity, price, OrderTif.GTC, ORDER_TIME);
    }

    private Order createMarketBuyOrder(String symbol, BigDecimal quantity) {
        return new Order(1L, symbol, TradeSide.BUY, OrderType.MARKET, quantity, null, OrderTif.GTC, ORDER_TIME);
    }

    private Order createLimitSellOrder(String symbol, BigDecimal quantity, BigDecimal price) {
        return new Order(1L, symbol, TradeSide.SELL, OrderType.LIMIT, quantity, price, OrderTif.GTC, ORDER_TIME);
    }

    private Order createMarketSellOrder(String symbol, BigDecimal quantity) {
        return new Order(1L, symbol, TradeSide.SELL, OrderType.MARKET, quantity, null, OrderTif.GTC, ORDER_TIME);
    }

    private Orderbook createOrderbook(String symbol, List<PriceLevel> asks, List<PriceLevel> bids) {
        return new Orderbook(symbol, asks, bids, ORDERBOOK_TIME);
    }

    @Nested
    @DisplayName("시간 제약 체크 시")
    class TimeConstraint {

        @Test
        void 주문시간이_호가시간보다_늦으면_SKIP을_반환한다() {
            // given
            LocalDateTime laterTime = ORDERBOOK_TIME.plusSeconds(1);
            Order order = new Order(1L, TEST_SYMBOL, TradeSide.BUY, OrderType.LIMIT,
                    BigDecimal.TEN, BigDecimal.valueOf(1000), OrderTif.GTC, laterTime);

            List<PriceLevel> asks = List.of(new PriceLevel(BigDecimal.valueOf(1000), BigDecimal.TEN));
            Orderbook orderbook = createOrderbook(TEST_SYMBOL, asks, List.of());

            // when
            MatchResult result = matchingEngine.match(order, orderbook);

            // then
            assertThat(result.isSkip()).isTrue();
            assertThat(result.fills()).isEmpty();
            assertThat(result.remainingQuantity()).isEqualTo(BigDecimal.TEN);
        }
    }

    @Nested
    @DisplayName("지정가 매수주문 체결 요청 시")
    class MatchLimitBuyOrder {

        @Test
        void 매도호가가_비어있으면_STOP을_반환한다() {
            // given
            Order order = createLimitBuyOrder(TEST_SYMBOL, BigDecimal.TEN, BigDecimal.valueOf(1000));
            Orderbook orderbook = createOrderbook(TEST_SYMBOL, List.of(), List.of());

            // when
            MatchResult result = matchingEngine.match(order, orderbook);

            // then
            assertThat(result.isStop()).isTrue();
            assertThat(result.fills()).isEmpty();
            assertThat(result.remainingQuantity()).isEqualTo(BigDecimal.TEN);
        }

        @Test
        void 주문가격이_매도호가보다_같거나_높으면_매도호가로_체결된다() {
            // given
            BigDecimal orderPrice = BigDecimal.valueOf(1500);
            BigDecimal quantity = BigDecimal.ONE;
            Order order = createLimitBuyOrder(TEST_SYMBOL, quantity, orderPrice);

            BigDecimal askPrice = BigDecimal.valueOf(1000);
            Orderbook orderbook = createOrderbook(TEST_SYMBOL,
                    List.of(new PriceLevel(askPrice, quantity)), List.of());

            // when
            MatchResult result = matchingEngine.match(order, orderbook);

            // then
            assertThat(result.isFilled()).isTrue();
            assertThat(result.isFullyFilled()).isTrue();
            assertThat(result.fills()).hasSize(1);
            assertThat(result.fills().getFirst().price()).isEqualTo(askPrice);
        }

        @Test
        void 주문가격이_매도호가보다_낮으면_STOP을_반환한다() {
            // given
            Order order = createLimitBuyOrder(TEST_SYMBOL, BigDecimal.ONE, BigDecimal.valueOf(1000));

            BigDecimal askPrice = BigDecimal.valueOf(5000);
            Orderbook orderbook = createOrderbook(TEST_SYMBOL,
                    List.of(new PriceLevel(askPrice, BigDecimal.ONE)), List.of());

            // when
            MatchResult result = matchingEngine.match(order, orderbook);

            // then
            assertThat(result.isStop()).isTrue();
            assertThat(result.fills()).isEmpty();
        }

        @Test
        void 단일_호가에서_수량이_충분하면_완전_체결된다() {
            // given
            BigDecimal price = BigDecimal.valueOf(1000);
            BigDecimal quantity = BigDecimal.valueOf(100);
            Order order = createLimitBuyOrder(TEST_SYMBOL, quantity, price);

            Orderbook orderbook = createOrderbook(TEST_SYMBOL,
                    List.of(new PriceLevel(price, quantity)), List.of());

            // when
            MatchResult result = matchingEngine.match(order, orderbook);

            // then
            assertThat(result.isFullyFilled()).isTrue();
            assertThat(result.fills()).hasSize(1);
            assertThat(result.remainingQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void 단일_호가에서_수량이_부족하면_부분_체결된다() {
            // given
            BigDecimal price = BigDecimal.valueOf(1000);
            BigDecimal orderQuantity = BigDecimal.valueOf(100);
            BigDecimal availableQuantity = BigDecimal.valueOf(50);
            Order order = createLimitBuyOrder(TEST_SYMBOL, orderQuantity, price);

            Orderbook orderbook = createOrderbook(TEST_SYMBOL,
                    List.of(new PriceLevel(price, availableQuantity)), List.of());

            // when
            MatchResult result = matchingEngine.match(order, orderbook);

            // then
            assertThat(result.isPartiallyFilled()).isTrue();
            assertThat(result.fills()).hasSize(1);
            assertThat(result.remainingQuantity()).isEqualByComparingTo(orderQuantity.subtract(availableQuantity));
        }

        @Test
        void 다중_호가에서_수량이_부족하면_다음_호가로_체결한다() {
            // given
            BigDecimal orderPrice = BigDecimal.valueOf(1000);
            BigDecimal orderQuantity = BigDecimal.valueOf(100);
            Order order = createLimitBuyOrder(TEST_SYMBOL, orderQuantity, orderPrice);

            BigDecimal askPrice1 = BigDecimal.valueOf(500);
            BigDecimal askPrice2 = BigDecimal.valueOf(600);
            BigDecimal qty1 = BigDecimal.valueOf(50);
            BigDecimal qty2 = BigDecimal.valueOf(40);
            Orderbook orderbook = createOrderbook(TEST_SYMBOL,
                    List.of(new PriceLevel(askPrice1, qty1), new PriceLevel(askPrice2, qty2)),
                    List.of());

            // when
            MatchResult result = matchingEngine.match(order, orderbook);

            // then
            assertThat(result.isPartiallyFilled()).isTrue();
            assertThat(result.fills()).hasSize(2);

            Fill fill1 = result.fills().get(0);
            assertThat(fill1.price()).isEqualTo(askPrice1);
            assertThat(fill1.quantity()).isEqualTo(qty1);

            Fill fill2 = result.fills().get(1);
            assertThat(fill2.price()).isEqualTo(askPrice2);
            assertThat(fill2.quantity()).isEqualTo(qty2);

            assertThat(result.remainingQuantity()).isEqualByComparingTo(orderQuantity.subtract(qty1).subtract(qty2));
        }
    }

    @Nested
    @DisplayName("시장가 매수주문 체결 요청 시")
    class MatchMarketBuyOrder {

        @Test
        void 전체_매도호가_수량만큼_체결된다() {
            // given
            BigDecimal orderQuantity = BigDecimal.valueOf(50);
            Order order = createMarketBuyOrder(TEST_SYMBOL, orderQuantity);

            BigDecimal askPrice1 = BigDecimal.valueOf(1000);
            BigDecimal askQty1 = BigDecimal.valueOf(10);
            BigDecimal askPrice2 = BigDecimal.valueOf(1100);
            BigDecimal askQty2 = BigDecimal.valueOf(10);
            Orderbook orderbook = createOrderbook(TEST_SYMBOL,
                    List.of(new PriceLevel(askPrice1, askQty1), new PriceLevel(askPrice2, askQty2)),
                    List.of());

            // when
            MatchResult result = matchingEngine.match(order, orderbook);

            // then
            assertThat(result.isPartiallyFilled()).isTrue();
            assertThat(result.fills()).hasSize(2);
            assertThat(result.fills().get(0).price()).isEqualTo(askPrice1);
            assertThat(result.fills().get(1).price()).isEqualTo(askPrice2);
            assertThat(result.remainingQuantity()).isEqualByComparingTo(orderQuantity.subtract(askQty1).subtract(askQty2));
        }
    }

    @Nested
    @DisplayName("지정가 매도주문 체결 요청 시")
    class MatchLimitSellOrder {

        @Test
        void 매수호가가_비어있으면_STOP을_반환한다() {
            // given
            Order order = createLimitSellOrder(TEST_SYMBOL, BigDecimal.TEN, BigDecimal.valueOf(1000));
            Orderbook orderbook = createOrderbook(TEST_SYMBOL, List.of(), List.of());

            // when
            MatchResult result = matchingEngine.match(order, orderbook);

            // then
            assertThat(result.isStop()).isTrue();
            assertThat(result.fills()).isEmpty();
        }

        @Test
        void 주문가격이_매수호가보다_같거나_낮으면_매수호가로_체결된다() {
            // given
            BigDecimal orderPrice = BigDecimal.valueOf(1000);
            BigDecimal quantity = BigDecimal.ONE;
            Order order = createLimitSellOrder(TEST_SYMBOL, quantity, orderPrice);

            BigDecimal bidPrice = BigDecimal.valueOf(1500);
            Orderbook orderbook = createOrderbook(TEST_SYMBOL, List.of(),
                    List.of(new PriceLevel(bidPrice, quantity)));

            // when
            MatchResult result = matchingEngine.match(order, orderbook);

            // then
            assertThat(result.isFullyFilled()).isTrue();
            assertThat(result.fills()).hasSize(1);
            assertThat(result.fills().getFirst().price()).isEqualTo(bidPrice);
        }

        @Test
        void 주문가격이_매수호가보다_높으면_STOP을_반환한다() {
            // given
            Order order = createLimitSellOrder(TEST_SYMBOL, BigDecimal.ONE, BigDecimal.valueOf(5000));

            BigDecimal bidPrice = BigDecimal.valueOf(1000);
            Orderbook orderbook = createOrderbook(TEST_SYMBOL, List.of(),
                    List.of(new PriceLevel(bidPrice, BigDecimal.ONE)));

            // when
            MatchResult result = matchingEngine.match(order, orderbook);

            // then
            assertThat(result.isStop()).isTrue();
            assertThat(result.fills()).isEmpty();
        }

        @Test
        void 단일_호가에서_수량이_충분하면_완전_체결된다() {
            // given
            BigDecimal price = BigDecimal.valueOf(1000);
            BigDecimal quantity = BigDecimal.valueOf(100);
            Order order = createLimitSellOrder(TEST_SYMBOL, quantity, price);

            Orderbook orderbook = createOrderbook(TEST_SYMBOL, List.of(),
                    List.of(new PriceLevel(price, quantity)));

            // when
            MatchResult result = matchingEngine.match(order, orderbook);

            // then
            assertThat(result.isFullyFilled()).isTrue();
            assertThat(result.fills()).hasSize(1);
            assertThat(result.remainingQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void 단일_호가에서_수량이_부족하면_부분_체결된다() {
            // given
            BigDecimal price = BigDecimal.valueOf(1000);
            BigDecimal orderQuantity = BigDecimal.valueOf(100);
            BigDecimal availableQuantity = BigDecimal.valueOf(50);
            Order order = createLimitSellOrder(TEST_SYMBOL, orderQuantity, price);

            Orderbook orderbook = createOrderbook(TEST_SYMBOL, List.of(),
                    List.of(new PriceLevel(price, availableQuantity)));

            // when
            MatchResult result = matchingEngine.match(order, orderbook);

            // then
            assertThat(result.isPartiallyFilled()).isTrue();
            assertThat(result.fills()).hasSize(1);
            assertThat(result.remainingQuantity()).isEqualByComparingTo(orderQuantity.subtract(availableQuantity));
        }

        @Test
        void 다중_호가에서_수량이_부족하면_다음_호가로_체결한다() {
            // given
            BigDecimal orderPrice = BigDecimal.valueOf(1000);
            BigDecimal orderQuantity = BigDecimal.valueOf(100);
            Order order = createLimitSellOrder(TEST_SYMBOL, orderQuantity, orderPrice);

            BigDecimal bidPrice1 = BigDecimal.valueOf(3000);
            BigDecimal bidPrice2 = BigDecimal.valueOf(2000);
            BigDecimal qty1 = BigDecimal.valueOf(50);
            BigDecimal qty2 = BigDecimal.valueOf(40);
            Orderbook orderbook = createOrderbook(TEST_SYMBOL, List.of(),
                    List.of(new PriceLevel(bidPrice1, qty1), new PriceLevel(bidPrice2, qty2)));

            // when
            MatchResult result = matchingEngine.match(order, orderbook);

            // then
            assertThat(result.isPartiallyFilled()).isTrue();
            assertThat(result.fills()).hasSize(2);

            Fill fill1 = result.fills().get(0);
            assertThat(fill1.price()).isEqualTo(bidPrice1);
            assertThat(fill1.quantity()).isEqualTo(qty1);

            Fill fill2 = result.fills().get(1);
            assertThat(fill2.price()).isEqualTo(bidPrice2);
            assertThat(fill2.quantity()).isEqualTo(qty2);

            assertThat(result.remainingQuantity()).isEqualByComparingTo(orderQuantity.subtract(qty1).subtract(qty2));
        }
    }

    @Nested
    @DisplayName("시장가 매도주문 체결 요청 시")
    class MatchMarketSellOrder {

        @Test
        void 전체_매수호가_수량만큼_체결된다() {
            // given
            BigDecimal orderQuantity = BigDecimal.valueOf(50);
            Order order = createMarketSellOrder(TEST_SYMBOL, orderQuantity);

            BigDecimal bidPrice1 = BigDecimal.valueOf(1100);
            BigDecimal bidQty1 = BigDecimal.valueOf(10);
            BigDecimal bidPrice2 = BigDecimal.valueOf(1000);
            BigDecimal bidQty2 = BigDecimal.valueOf(10);
            Orderbook orderbook = createOrderbook(TEST_SYMBOL, List.of(),
                    List.of(new PriceLevel(bidPrice1, bidQty1), new PriceLevel(bidPrice2, bidQty2)));

            // when
            MatchResult result = matchingEngine.match(order, orderbook);

            // then
            assertThat(result.isPartiallyFilled()).isTrue();
            assertThat(result.fills()).hasSize(2);
            assertThat(result.fills().get(0).price()).isEqualTo(bidPrice1);
            assertThat(result.fills().get(1).price()).isEqualTo(bidPrice2);
            assertThat(result.remainingQuantity()).isEqualByComparingTo(orderQuantity.subtract(bidQty1).subtract(bidQty2));
        }
    }
}
