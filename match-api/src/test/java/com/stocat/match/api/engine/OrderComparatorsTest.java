package com.stocat.match.api.engine;

import com.stocat.match.domain.TradeSide;
import com.stocat.match.domain.order.Order;
import com.stocat.match.domain.order.OrderTif;
import com.stocat.match.domain.order.OrderType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderComparatorsTest {

    private static final String TEST_SYMBOL = "NVDA";

    @Nested
    @DisplayName("매수주문_정렬_시")
    class BuyOrderComparatorTest {

        @Test
        void 시장가_주문이_지정가_주문보다_우선한다() {
            // given
            Order marketOrder = createMarketBuyOrder("2");
            Order limitOrder = createLimitBuyOrder(BigDecimal.valueOf(10000), "1");

            Comparator<Order> comparator = OrderComparators.buyOrderComparator();

            // when
            List<Order> sorted = Arrays.asList(limitOrder, marketOrder);
            sorted.sort(comparator);

            // then
            assertThat(sorted.get(0)).isEqualTo(marketOrder);
            assertThat(sorted.get(1)).isEqualTo(limitOrder);
        }

        @Test
        void 지정가_주문끼리는_가격이_높은_주문이_우선한다() {
            // given
            Order highPriceOrder = createLimitBuyOrder(BigDecimal.valueOf(10000), "2");
            Order lowPriceOrder = createLimitBuyOrder(BigDecimal.valueOf(5000), "1");

            Comparator<Order> comparator = OrderComparators.buyOrderComparator();

            // when
            List<Order> sorted = Arrays.asList(lowPriceOrder, highPriceOrder);
            sorted.sort(comparator);

            // then
            assertThat(sorted.get(0)).isEqualTo(highPriceOrder);
            assertThat(sorted.get(1)).isEqualTo(lowPriceOrder);
        }

        @Test
        void 가격이_같으면_seq가_작은_주문이_우선한다() {
            // given
            BigDecimal samePrice = BigDecimal.valueOf(10000);
            Order firstOrder = createLimitBuyOrder(samePrice, "1");
            Order secondOrder = createLimitBuyOrder(samePrice, "2");

            Comparator<Order> comparator = OrderComparators.buyOrderComparator();

            // when
            List<Order> sorted = Arrays.asList(secondOrder, firstOrder);
            sorted.sort(comparator);

            // then
            assertThat(sorted.get(0)).isEqualTo(firstOrder);
            assertThat(sorted.get(1)).isEqualTo(secondOrder);
        }

        @Test
        void 시장가_주문끼리는_seq가_작은_주문이_우선한다() {
            // given
            Order firstMarketOrder = createMarketBuyOrder("1");
            Order secondMarketOrder = createMarketBuyOrder("2");

            Comparator<Order> comparator = OrderComparators.buyOrderComparator();

            // when
            List<Order> sorted = Arrays.asList(secondMarketOrder, firstMarketOrder);
            sorted.sort(comparator);

            // then
            assertThat(sorted.get(0)).isEqualTo(firstMarketOrder);
            assertThat(sorted.get(1)).isEqualTo(secondMarketOrder);
        }

        @Test
        void 복합_정렬_우선순위가_올바르게_적용된다() {
            // given
            Order marketOrder1 = createMarketBuyOrder("3");
            Order marketOrder2 = createMarketBuyOrder("4");
            Order highPriceOrder = createLimitBuyOrder(BigDecimal.valueOf(10000), "1");
            Order lowPriceOrder = createLimitBuyOrder(BigDecimal.valueOf(5000), "2");

            Comparator<Order> comparator = OrderComparators.buyOrderComparator();

            // when
            List<Order> sorted = Arrays.asList(lowPriceOrder, marketOrder2, highPriceOrder, marketOrder1);
            sorted.sort(comparator);

            // then
            assertThat(sorted).containsExactly(marketOrder1, marketOrder2, highPriceOrder, lowPriceOrder);
        }
    }

    @Nested
    @DisplayName("매도주문_정렬_시")
    class SellOrderComparatorTest {

        @Test
        void 시장가_주문이_지정가_주문보다_우선한다() {
            // given
            Order marketOrder = createMarketSellOrder("2");
            Order limitOrder = createLimitSellOrder(BigDecimal.valueOf(10000), "1");

            Comparator<Order> comparator = OrderComparators.sellOrderComparator();

            // when
            List<Order> sorted = Arrays.asList(limitOrder, marketOrder);
            sorted.sort(comparator);

            // then
            assertThat(sorted.get(0)).isEqualTo(marketOrder);
            assertThat(sorted.get(1)).isEqualTo(limitOrder);
        }

        @Test
        void 지정가_주문끼리는_가격이_낮은_주문이_우선한다() {
            // given
            Order highPriceOrder = createLimitSellOrder(BigDecimal.valueOf(10000), "1");
            Order lowPriceOrder = createLimitSellOrder(BigDecimal.valueOf(5000), "2");

            Comparator<Order> comparator = OrderComparators.sellOrderComparator();

            // when
            List<Order> sorted = Arrays.asList(highPriceOrder, lowPriceOrder);
            sorted.sort(comparator);

            // then
            assertThat(sorted.get(0)).isEqualTo(lowPriceOrder);
            assertThat(sorted.get(1)).isEqualTo(highPriceOrder);
        }

        @Test
        void 가격이_같으면_seq가_작은_주문이_우선한다() {
            // given
            BigDecimal samePrice = BigDecimal.valueOf(10000);
            Order firstOrder = createLimitSellOrder(samePrice, "1");
            Order secondOrder = createLimitSellOrder(samePrice, "2");

            Comparator<Order> comparator = OrderComparators.sellOrderComparator();

            // when
            List<Order> sorted = Arrays.asList(secondOrder, firstOrder);
            sorted.sort(comparator);

            // then
            assertThat(sorted.get(0)).isEqualTo(firstOrder);
            assertThat(sorted.get(1)).isEqualTo(secondOrder);
        }

        @Test
        void 시장가_주문끼리는_seq가_작은_주문이_우선한다() {
            // given
            Order firstMarketOrder = createMarketSellOrder("1");
            Order secondMarketOrder = createMarketSellOrder("2");

            Comparator<Order> comparator = OrderComparators.sellOrderComparator();

            // when
            List<Order> sorted = Arrays.asList(secondMarketOrder, firstMarketOrder);
            sorted.sort(comparator);

            // then
            assertThat(sorted.get(0)).isEqualTo(firstMarketOrder);
            assertThat(sorted.get(1)).isEqualTo(secondMarketOrder);
        }

        @Test
        void 복합_정렬_우선순위가_올바르게_적용된다() {
            // given
            Order marketOrder1 = createMarketSellOrder("3");
            Order marketOrder2 = createMarketSellOrder("4");
            Order highPriceOrder = createLimitSellOrder(BigDecimal.valueOf(10000), "2");
            Order lowPriceOrder = createLimitSellOrder(BigDecimal.valueOf(5000), "1");

            Comparator<Order> comparator = OrderComparators.sellOrderComparator();

            // when
            List<Order> sorted = Arrays.asList(highPriceOrder, marketOrder2, lowPriceOrder, marketOrder1);
            sorted.sort(comparator);

            // then
            assertThat(sorted).containsExactly(marketOrder1, marketOrder2, lowPriceOrder, highPriceOrder);
        }
    }

    // Test Helper Methods

    private Order createLimitBuyOrder(BigDecimal price, String seq) {
        return new Order(
                1L,
                TEST_SYMBOL,
                TradeSide.BUY,
                OrderType.LIMIT,
                BigDecimal.valueOf(10),
                price,
                OrderTif.GTC,
                seq
        );
    }

    private Order createMarketBuyOrder(String seq) {
        return new Order(
                1L,
                TEST_SYMBOL,
                TradeSide.BUY,
                OrderType.MARKET,
                BigDecimal.valueOf(10),
                null,
                OrderTif.GTC,
                seq
        );
    }

    private Order createLimitSellOrder(BigDecimal price, String seq) {
        return new Order(
                1L,
                TEST_SYMBOL,
                TradeSide.SELL,
                OrderType.LIMIT,
                BigDecimal.valueOf(10),
                price,
                OrderTif.GTC,
                seq
        );
    }

    private Order createMarketSellOrder(String seq) {
        return new Order(
                1L,
                TEST_SYMBOL,
                TradeSide.SELL,
                OrderType.MARKET,
                BigDecimal.valueOf(10),
                null,
                OrderTif.GTC,
                seq
        );
    }
}