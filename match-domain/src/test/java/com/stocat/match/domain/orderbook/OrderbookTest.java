package com.stocat.match.domain.orderbook;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderbookTest {
    private static final String TEST_SYMBOL = "NVDA";
    
    @Nested
    @DisplayName("Orderbook 생성 시")
    class CreateOrderbook {

        @Test
        void 정상적으로_생성된다() {
            // given
            String symbol = TEST_SYMBOL;
            List<PriceLevel> asks = List.of(
                    new PriceLevel(BigDecimal.valueOf(100), BigDecimal.valueOf(10)),
                    new PriceLevel(BigDecimal.valueOf(110), BigDecimal.valueOf(20))
            );
            List<PriceLevel> bids = List.of(
                    new PriceLevel(BigDecimal.valueOf(90), BigDecimal.valueOf(15)),
                    new PriceLevel(BigDecimal.valueOf(80), BigDecimal.valueOf(25))
            );

            // when
            Orderbook orderbook = new Orderbook(symbol, asks, bids);

            // then
            assertThat(orderbook.symbol()).isEqualTo(symbol);
            assertThat(orderbook.asks()).hasSize(2);
            assertThat(orderbook.bids()).hasSize(2);
        }

        @Test
        void 빈_호가_리스트로_생성된다() {
            // given
            String symbol = TEST_SYMBOL;

            // when
            Orderbook orderbook = new Orderbook(symbol, Collections.emptyList(), Collections.emptyList());

            // then
            assertThat(orderbook.symbol()).isEqualTo(symbol);
            assertThat(orderbook.asks()).isEmpty();
            assertThat(orderbook.bids()).isEmpty();
        }

        @Test
        void null_호가_리스트로_생성된다() {
            // given
            String symbol = TEST_SYMBOL;

            // when
            Orderbook orderbook = new Orderbook(symbol, null, null);

            // then
            assertThat(orderbook.symbol()).isEqualTo(symbol);
            assertThat(orderbook.asks()).isNull();
            assertThat(orderbook.bids()).isNull();
        }
    }

    @Nested
    @DisplayName("symbol 검증 시")
    class ValidateSymbol {

        @Test
        void symbol이_null이면_예외가_발생한다() {
            // given
            List<PriceLevel> asks = Collections.emptyList();
            List<PriceLevel> bids = Collections.emptyList();

            // when & then
            assertThatThrownBy(() -> new Orderbook(null, asks, bids))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void symbol이_빈_문자열이면_예외가_발생한다() {
            // given
            List<PriceLevel> asks = Collections.emptyList();
            List<PriceLevel> bids = Collections.emptyList();

            // when & then
            assertThatThrownBy(() -> new Orderbook("", asks, bids))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("매도호가(asks) 검증 시")
    class ValidateAsks {

        @Test
        void 오름차순으로_정렬되어_있으면_정상_생성된다() {
            // given
            String symbol = TEST_SYMBOL;
            List<PriceLevel> asks = List.of(
                    new PriceLevel(BigDecimal.valueOf(100), BigDecimal.valueOf(10)),
                    new PriceLevel(BigDecimal.valueOf(110), BigDecimal.valueOf(20)),
                    new PriceLevel(BigDecimal.valueOf(120), BigDecimal.valueOf(30))
            );

            // when
            Orderbook orderbook = new Orderbook(symbol, asks, Collections.emptyList());

            // then
            assertThat(orderbook.asks()).hasSize(3);
        }

        @Test
        void 내림차순으로_정렬되어_있으면_예외가_발생한다() {
            // given
            String symbol = TEST_SYMBOL;
            List<PriceLevel> asks = List.of(
                    new PriceLevel(BigDecimal.valueOf(120), BigDecimal.valueOf(10)),
                    new PriceLevel(BigDecimal.valueOf(110), BigDecimal.valueOf(20)),
                    new PriceLevel(BigDecimal.valueOf(100), BigDecimal.valueOf(30))
            );

            // when & then
            assertThatThrownBy(() -> new Orderbook(symbol, asks, Collections.emptyList()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("오름차순");
        }

        @Test
        void 동일한_가격이_연속되면_예외가_발생한다() {
            // given
            String symbol = TEST_SYMBOL;
            List<PriceLevel> asks = List.of(
                    new PriceLevel(BigDecimal.valueOf(100), BigDecimal.valueOf(10)),
                    new PriceLevel(BigDecimal.valueOf(100), BigDecimal.valueOf(20))
            );

            // when & then
            assertThatThrownBy(() -> new Orderbook(symbol, asks, Collections.emptyList()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("오름차순");
        }

        @Test
        void 단일_호가는_정상_생성된다() {
            // given
            String symbol = TEST_SYMBOL;
            List<PriceLevel> asks = List.of(
                    new PriceLevel(BigDecimal.valueOf(100), BigDecimal.valueOf(10))
            );

            // when
            Orderbook orderbook = new Orderbook(symbol, asks, Collections.emptyList());

            // then
            assertThat(orderbook.asks()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("매수호가(bids) 검증 시")
    class ValidateBids {

        @Test
        void 내림차순으로_정렬되어_있으면_정상_생성된다() {
            // given
            String symbol = TEST_SYMBOL;
            List<PriceLevel> bids = List.of(
                    new PriceLevel(BigDecimal.valueOf(90), BigDecimal.valueOf(10)),
                    new PriceLevel(BigDecimal.valueOf(80), BigDecimal.valueOf(20)),
                    new PriceLevel(BigDecimal.valueOf(70), BigDecimal.valueOf(30))
            );

            // when
            Orderbook orderbook = new Orderbook(symbol, Collections.emptyList(), bids);

            // then
            assertThat(orderbook.bids()).hasSize(3);
        }

        @Test
        void 오름차순으로_정렬되어_있으면_예외가_발생한다() {
            // given
            String symbol = TEST_SYMBOL;
            List<PriceLevel> bids = List.of(
                    new PriceLevel(BigDecimal.valueOf(70), BigDecimal.valueOf(10)),
                    new PriceLevel(BigDecimal.valueOf(80), BigDecimal.valueOf(20)),
                    new PriceLevel(BigDecimal.valueOf(90), BigDecimal.valueOf(30))
            );

            // when & then
            assertThatThrownBy(() -> new Orderbook(symbol, Collections.emptyList(), bids))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("내림차순");
        }

        @Test
        void 동일한_가격이_연속되면_예외가_발생한다() {
            // given
            String symbol = TEST_SYMBOL;
            List<PriceLevel> bids = List.of(
                    new PriceLevel(BigDecimal.valueOf(90), BigDecimal.valueOf(10)),
                    new PriceLevel(BigDecimal.valueOf(90), BigDecimal.valueOf(20))
            );

            // when & then
            assertThatThrownBy(() -> new Orderbook(symbol, Collections.emptyList(), bids))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("내림차순");
        }

        @Test
        void 단일_호가는_정상_생성된다() {
            // given
            String symbol = TEST_SYMBOL;
            List<PriceLevel> bids = List.of(
                    new PriceLevel(BigDecimal.valueOf(90), BigDecimal.valueOf(10))
            );

            // when
            Orderbook orderbook = new Orderbook(symbol, Collections.emptyList(), bids);

            // then
            assertThat(orderbook.bids()).hasSize(1);
        }
    }
}
