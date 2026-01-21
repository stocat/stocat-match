package com.stocat.match.api.engine;

import com.stocat.match.domain.TradeSide;
import com.stocat.match.domain.fill.Fill;
import com.stocat.match.domain.fill.FillResult;
import com.stocat.match.domain.order.Order;
import com.stocat.match.domain.order.OrderTif;
import com.stocat.match.domain.order.OrderType;
import com.stocat.match.domain.orderbook.Orderbook;
import com.stocat.match.domain.orderbook.PriceLevel;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class MatchingEngineTest {
    private MatchingEngine matchingEngine;

    public static final String TEST_SYMBOL = "NVDA";

    @BeforeEach
    void setUp() {
        matchingEngine = new MatchingEngine();
    }

    
    public static Order createLimitBuyOrder(String symbol, BigDecimal quantity, BigDecimal price) {
        return new Order(
                1L,
                symbol,
                TradeSide.BUY,
                OrderType.LIMIT,
                quantity,
                price,
                OrderTif.GTC,
                "1"
        );
    }

    public static Order createMarketSellOrder(String symbol, BigDecimal quantity) {
        return new Order(
                1L,
                symbol,
                TradeSide.BUY,
                OrderType.MARKET,
                quantity,
                null,
                OrderTif.GTC,
                "1"
        );
    }

    public static Order createLimitSellOrder(String symbol, BigDecimal quantity, BigDecimal price) {
        return new Order(
                1L,
                symbol,
                TradeSide.SELL,
                OrderType.LIMIT,
                quantity,
                price,
                OrderTif.GTC,
                "1"
        );
    }

    @Nested
    @DisplayName("지정가_매수주문_체결_요청_시")
    class MatchLimitBuyOrderTest {

        @Test
        void 주문가격이_매도호가보다_같거나_높으면_매도호가로_체결된다() {
            // given
            BigDecimal orderPrice = BigDecimal.valueOf(1500);
            BigDecimal quantity = BigDecimal.valueOf(1);

            Order order = createLimitBuyOrder(TEST_SYMBOL, quantity, orderPrice);

            BigDecimal askPrice = BigDecimal.valueOf(1000);
            List<PriceLevel> asks = List.of(new PriceLevel(askPrice, quantity));
            Orderbook orderbook = new Orderbook(TEST_SYMBOL, asks, List.of());

            // when
            FillResult fillResult = matchingEngine.matchBuyOrder(order, orderbook);

            // then
            Assertions.assertThat(fillResult).satisfies(result -> {
                Assertions.assertThat(result.fills()).hasSize(1);
                Assertions.assertThat(result.fills().get(0).price()).isEqualTo(askPrice);
                Assertions.assertThat(result.remainingOrder()).isNull();
                Assertions.assertThat(result.totalFilledQuantity()).isEqualTo(quantity);
            });
        }

        @Test
        void 주문가격이_매도호가보다_낮으면_체결되지_않는다() {
            // given
            BigDecimal orderPrice = BigDecimal.valueOf(1000);
            BigDecimal quantity = BigDecimal.valueOf(1);

            Order order = createLimitBuyOrder(TEST_SYMBOL, quantity, orderPrice);

            BigDecimal askPrice = BigDecimal.valueOf(5000);
            List<PriceLevel> asks = List.of(new PriceLevel(askPrice, quantity));
            Orderbook orderbook = new Orderbook(TEST_SYMBOL, asks, List.of());

            // when
            FillResult fillResult = matchingEngine.matchBuyOrder(order, orderbook);

            // then
            Assertions.assertThat(fillResult).satisfies(result -> {
                Assertions.assertThat(result.fills()).isEmpty();
                Assertions.assertThat(result.remainingOrder().quantity()).isEqualTo(quantity);
                Assertions.assertThat(result.totalFilledQuantity()).isZero();
            });
        }

        @Test
        void 단일_호가에서_매도호가_수량이_충분하면_체결_성공한다() {
            // given
            BigDecimal price = BigDecimal.valueOf(1000);
            BigDecimal quantity = BigDecimal.valueOf(100);

            Order order = createLimitBuyOrder(TEST_SYMBOL, quantity, price);

            List<PriceLevel> asks = List.of(new PriceLevel(price, quantity));
            Orderbook orderbook = new Orderbook(TEST_SYMBOL, asks, List.of());

            // when
            FillResult fillResult = matchingEngine.matchBuyOrder(order, orderbook);

            // then
            Assertions.assertThat(fillResult).satisfies(result -> {
                Assertions.assertThat(result.fills()).hasSize(1);
                Assertions.assertThat(result.fills().get(0).price()).isEqualTo(price);
                Assertions.assertThat(result.remainingOrder()).isNull();
                Assertions.assertThat(result.totalFilledQuantity()).isEqualTo(quantity);
            });
        }


        @Test
        void 단일_호가에서_매도호가_수량이_부족하면_부분체결한다() {
            // given
            BigDecimal price = BigDecimal.valueOf(1000);
            BigDecimal orderQuantity = BigDecimal.valueOf(100);

            Order order = createLimitBuyOrder(TEST_SYMBOL, orderQuantity, price);

            BigDecimal availableQuantity = BigDecimal.valueOf(50);
            List<PriceLevel> asks = List.of(new PriceLevel(price, availableQuantity));
            Orderbook orderbook = new Orderbook(TEST_SYMBOL, asks, List.of());

            // when
            FillResult fillResult = matchingEngine.matchBuyOrder(order, orderbook);

            // then
            Assertions.assertThat(fillResult).satisfies(result -> {
                Assertions.assertThat(result.fills()).hasSize(1);
                Assertions.assertThat(result.fills().get(0).price()).isEqualTo(price);
                Assertions.assertThat(result.remainingOrder().quantity()).isEqualTo(orderQuantity.subtract(availableQuantity));
                Assertions.assertThat(result.totalFilledQuantity()).isEqualTo(availableQuantity);
            });
        }

        @Test
        void 다중_호가에서_최저매수호가_수량이_부족하면_다음호가로_체결한다() {
            // given
            BigDecimal orderPrice = BigDecimal.valueOf(1000);
            BigDecimal orderQuantity = BigDecimal.valueOf(100);

            Order order = createLimitBuyOrder(TEST_SYMBOL, orderQuantity, orderPrice);

            BigDecimal askPrice1 = BigDecimal.valueOf(500);
            BigDecimal askPrice2 = BigDecimal.valueOf(600);
            BigDecimal availableQuantity1 = BigDecimal.valueOf(50);
            BigDecimal availableQuantity2 = BigDecimal.valueOf(40);
            List<PriceLevel> asks = List.of(
                    new PriceLevel(askPrice1, availableQuantity1),
                    new PriceLevel(askPrice2, availableQuantity2)
            );
            Orderbook orderbook = new Orderbook(TEST_SYMBOL, asks, List.of());

            // when
            FillResult fillResult = matchingEngine.matchBuyOrder(order, orderbook);

            // then
            Assertions.assertThat(fillResult).satisfies(result -> {
                Assertions.assertThat(result.fills()).hasSize(2);

                Fill fill1 = result.fills().get(0);
                Assertions.assertThat(fill1.price()).isEqualTo(askPrice1);
                Assertions.assertThat(fill1.quantity()).isEqualTo(availableQuantity1);

                Fill fill2 = result.fills().get(1);
                Assertions.assertThat(fill2.price()).isEqualTo(askPrice2);
                Assertions.assertThat(fill2.quantity()).isEqualTo(availableQuantity2);

                BigDecimal expectedRemainingQuantity = orderQuantity.subtract(availableQuantity1).subtract(availableQuantity2);
                Assertions.assertThat(result.remainingOrder().quantity()).isEqualTo(expectedRemainingQuantity);

                BigDecimal totalFilledQuantity = availableQuantity1.add(availableQuantity2);
                Assertions.assertThat(result.totalFilledQuantity()).isEqualTo(totalFilledQuantity);
            });
        }
    }

    @Nested
    @DisplayName("시장가_매수주문_체결_요청_시")
    class MatchMarketBuyOrderTest {

        @Test
        void 전체_매도호가_수량만큼_체결된다() {
            // given
            BigDecimal orderQuantity = BigDecimal.valueOf(50);
            Order order = createMarketSellOrder(TEST_SYMBOL, orderQuantity);

            BigDecimal askPrice1 = BigDecimal.valueOf(1000);
            BigDecimal askQuantity1 = BigDecimal.valueOf(10);
            BigDecimal askPrice2 = BigDecimal.valueOf(1100);
            BigDecimal askQuantity2 = BigDecimal.valueOf(10);
            List<PriceLevel> asks = List.of(
                    new PriceLevel(askPrice1, askQuantity1),
                    new PriceLevel(askPrice2, askQuantity2)
            );
            Orderbook orderbook = new Orderbook(TEST_SYMBOL, asks, List.of());

            // when
            FillResult fillResult = matchingEngine.matchBuyOrder(order, orderbook);

            // then
            Assertions.assertThat(fillResult).satisfies(result -> {
                Assertions.assertThat(result.fills()).hasSize(2);

                Fill fill1 = result.fills().get(0);
                Assertions.assertThat(fill1.price()).isEqualTo(askPrice1);
                Assertions.assertThat(fill1.quantity()).isEqualTo(askQuantity1);

                Fill fill2 = result.fills().get(1);
                Assertions.assertThat(fill2.price()).isEqualTo(askPrice2);
                Assertions.assertThat(fill2.quantity()).isEqualTo(askQuantity2);

                BigDecimal expectedRemainingQuantity = orderQuantity.subtract(askQuantity1).subtract(askQuantity2);
                Assertions.assertThat(result.remainingOrder().quantity()).isEqualTo(expectedRemainingQuantity);
                Assertions.assertThat(result.totalFilledQuantity()).isEqualTo(askQuantity1.add(askQuantity2));
            });
        }
    }

    @Nested
    @DisplayName("지정가_매도주문_체결_요청_시")
    class MatchSellOrderTest {

        @Test
        void 주문가격이_매수호가보다_같거나_낮으면_매수호가로_체결된다() {
            // given
            BigDecimal orderPrice = BigDecimal.valueOf(1000);
            BigDecimal quantity = BigDecimal.valueOf(1);

            Order order = createLimitSellOrder(TEST_SYMBOL, quantity, orderPrice);

            BigDecimal bidPrice = BigDecimal.valueOf(1500);
            List<PriceLevel> bids = List.of(new PriceLevel(bidPrice, quantity));
            Orderbook orderbook = new Orderbook(TEST_SYMBOL, List.of(), bids);

            // when
            FillResult fillResult = matchingEngine.matchSellOrder(order, orderbook);

            // then
            Assertions.assertThat(fillResult).satisfies(result -> {
                Assertions.assertThat(result.fills()).hasSize(1);
                Assertions.assertThat(result.fills().get(0).price()).isEqualTo(bidPrice);
                Assertions.assertThat(result.remainingOrder()).isNull();
                Assertions.assertThat(result.totalFilledQuantity()).isEqualTo(quantity);
            });
        }

        @Test
        void 주문가격이_매수호가보다_높으면_체결되지_않는다() {
            // given
            BigDecimal orderPrice = BigDecimal.valueOf(5000);
            BigDecimal quantity = BigDecimal.valueOf(1);

            Order order = createLimitBuyOrder(TEST_SYMBOL, quantity, orderPrice);

            BigDecimal bidPrice = BigDecimal.valueOf(1000);
            List<PriceLevel> bids = List.of(new PriceLevel(bidPrice, quantity));
            Orderbook orderbook = new Orderbook(TEST_SYMBOL, List.of(), bids);

            // when
            FillResult fillResult = matchingEngine.matchSellOrder(order, orderbook);

            // then
            Assertions.assertThat(fillResult).satisfies(result -> {
                Assertions.assertThat(result.fills()).isEmpty();
                Assertions.assertThat(result.remainingOrder().quantity()).isEqualTo(quantity);
                Assertions.assertThat(result.totalFilledQuantity()).isZero();
            });
        }

        @Test
        void 단일_호가에서_매수호가_수량이_충분하면_체결_성공한다() {
            // given
            BigDecimal price = BigDecimal.valueOf(1000);
            BigDecimal quantity = BigDecimal.valueOf(100);

            Order order = createLimitBuyOrder(TEST_SYMBOL, quantity, price);

            List<PriceLevel> bids = List.of(new PriceLevel(price, quantity));
            Orderbook orderbook = new Orderbook(TEST_SYMBOL, List.of(), bids);

            // when
            FillResult fillResult = matchingEngine.matchSellOrder(order, orderbook);

            // then
            Assertions.assertThat(fillResult).satisfies(result -> {
                Assertions.assertThat(result.fills()).hasSize(1);
                Assertions.assertThat(result.fills().get(0).price()).isEqualTo(price);
                Assertions.assertThat(result.remainingOrder()).isNull();
                Assertions.assertThat(result.totalFilledQuantity()).isEqualTo(quantity);
            });
        }


        @Test
        void 단일_호가에서_매수호가_수량이_부족하면_부분체결한다() {
            // given
            BigDecimal price = BigDecimal.valueOf(1000);
            BigDecimal orderQuantity = BigDecimal.valueOf(100);

            Order order = createLimitBuyOrder(TEST_SYMBOL, orderQuantity, price);

            BigDecimal availableQuantity = BigDecimal.valueOf(50);
            List<PriceLevel> bids = List.of(new PriceLevel(price, availableQuantity));
            Orderbook orderbook = new Orderbook(TEST_SYMBOL, List.of(), bids);

            // when
            FillResult fillResult = matchingEngine.matchSellOrder(order, orderbook);

            // then
            Assertions.assertThat(fillResult).satisfies(result -> {
                Assertions.assertThat(result.fills()).hasSize(1);
                Assertions.assertThat(result.fills().get(0).price()).isEqualTo(price);
                Assertions.assertThat(result.remainingOrder().quantity()).isEqualTo(orderQuantity.subtract(availableQuantity));
                Assertions.assertThat(result.totalFilledQuantity()).isEqualTo(availableQuantity);
            });
        }

        @Test
        void 다중_호가에서_최우선매수호가_수량이_부족하면_다음호가로_체결한다() {
            // given
            BigDecimal orderPrice = BigDecimal.valueOf(1000);
            BigDecimal orderQuantity = BigDecimal.valueOf(100);

            Order order = createLimitBuyOrder(TEST_SYMBOL, orderQuantity, orderPrice);

            BigDecimal bidPrice1 = BigDecimal.valueOf(3000);
            BigDecimal bidPrice2 = BigDecimal.valueOf(2000);
            BigDecimal bidQuantity1 = BigDecimal.valueOf(50);
            BigDecimal bidQuantity2 = BigDecimal.valueOf(40);
            List<PriceLevel> bids = List.of(
                    new PriceLevel(bidPrice1, bidQuantity1),
                    new PriceLevel(bidPrice2, bidQuantity2)
            );
            Orderbook orderbook = new Orderbook(TEST_SYMBOL, List.of(), bids);

            // when
            FillResult fillResult = matchingEngine.matchSellOrder(order, orderbook);

            // then
            Assertions.assertThat(fillResult).satisfies(result -> {
                Assertions.assertThat(result.fills()).hasSize(2);

                Fill fill1 = result.fills().get(0);
                Assertions.assertThat(fill1.price()).isEqualTo(bidPrice1);
                Assertions.assertThat(fill1.quantity()).isEqualTo(bidQuantity1);

                Fill fill2 = result.fills().get(1);
                Assertions.assertThat(fill2.price()).isEqualTo(bidPrice2);
                Assertions.assertThat(fill2.quantity()).isEqualTo(bidQuantity2);

                BigDecimal expectedRemainingQuantity = orderQuantity.subtract(bidQuantity1).subtract(bidQuantity2);
                Assertions.assertThat(result.remainingOrder().quantity()).isEqualTo(expectedRemainingQuantity);

                BigDecimal totalFilledQuantity = bidQuantity1.add(bidQuantity2);
                Assertions.assertThat(result.totalFilledQuantity()).isEqualTo(totalFilledQuantity);
            });
        }
    }

    @Nested
    @DisplayName("시장가_매도주문_체결_요청_시")
    class MatchMarketSellOrderTest {

        @Test
        void 전체_매도호가_수량만큼_체결된다() {
            // given
            BigDecimal orderQuantity = BigDecimal.valueOf(50);
            Order order = createMarketSellOrder(TEST_SYMBOL, orderQuantity);

            BigDecimal askPrice1 = BigDecimal.valueOf(1000);
            BigDecimal askQuantity1 = BigDecimal.valueOf(10);
            BigDecimal askPrice2 = BigDecimal.valueOf(1100);
            BigDecimal askQuantity2 = BigDecimal.valueOf(10);
            List<PriceLevel> asks = List.of(
                    new PriceLevel(askPrice1, askQuantity1),
                    new PriceLevel(askPrice2, askQuantity2)
            );
            Orderbook orderbook = new Orderbook(TEST_SYMBOL, asks, List.of());

            // when
            FillResult fillResult = matchingEngine.matchBuyOrder(order, orderbook);

            // then
            Assertions.assertThat(fillResult).satisfies(result -> {
                Assertions.assertThat(result.fills()).hasSize(2);

                Fill fill1 = result.fills().get(0);
                Assertions.assertThat(fill1.price()).isEqualTo(askPrice1);
                Assertions.assertThat(fill1.quantity()).isEqualTo(askQuantity1);

                Fill fill2 = result.fills().get(1);
                Assertions.assertThat(fill2.price()).isEqualTo(askPrice2);
                Assertions.assertThat(fill2.quantity()).isEqualTo(askQuantity2);

                BigDecimal expectedRemainingQuantity = orderQuantity.subtract(askQuantity1).subtract(askQuantity2);
                Assertions.assertThat(result.remainingOrder().quantity()).isEqualTo(expectedRemainingQuantity);
                Assertions.assertThat(result.totalFilledQuantity()).isEqualTo(askQuantity1.add(askQuantity2));
            });
        }
    }
}