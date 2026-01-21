package com.stocat.match.api.engine;

import com.stocat.match.domain.TradeSide;
import com.stocat.match.domain.order.Order;
import com.stocat.match.domain.order.OrderTif;
import com.stocat.match.domain.order.OrderType;
import com.stocat.match.redis.stream.OrderbookStreamClient;
import com.stocat.match.redis.stream.OrderbookStreamMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class StockSessionManagerTest {

    @Mock
    private MatchingWorkerFactory workerFactory;

    @Mock
    private OrderbookStreamClient streamClient;

    @Mock
    private MatchingWorker mockWorker;

    @Mock
    private MatchingWorker mockWorker2;

    private StockSessionManager sessionManager;

    public static final String TEST_SYMBOL = "NVDA";

    @BeforeEach
    void setUp() {
        sessionManager = new StockSessionManager(workerFactory, streamClient);
    }

    private Order createSimpleOrder(String symbol) {
        return new Order(
                1L,
                symbol,
                TradeSide.BUY,
                OrderType.LIMIT,
                BigDecimal.valueOf(10),
                BigDecimal.valueOf(50000),
                OrderTif.GTC,
                "1"
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, StockSession> getSessions() throws Exception {
        Field sessionsField = StockSessionManager.class.getDeclaredField("sessions");
        sessionsField.setAccessible(true);
        return (Map<String, StockSession>) sessionsField.get(sessionManager);
    }

    @Nested
    @DisplayName("종목 등록 시")
    class RegisterSymbol {

        @Test
        void 세션이_정상적으로_등록된다() throws Exception {
            // given
            given(streamClient.createConsumerGroup(anyString())).willReturn(Mono.empty());
            given(streamClient.subscribe(anyString(), anyString())).willReturn(Flux.never());
            given(workerFactory.create(TEST_SYMBOL)).willReturn(mockWorker);

            // when
            sessionManager.registerSymbol(TEST_SYMBOL);

            // then
            Map<String, StockSession> sessions = getSessions();
            assertThat(sessions).containsKey(TEST_SYMBOL);
            assertThat(sessions.get(TEST_SYMBOL).symbol()).isEqualTo(TEST_SYMBOL);
            assertThat(sessions.get(TEST_SYMBOL).worker()).isEqualTo(mockWorker);
            then(workerFactory).should().create(TEST_SYMBOL);
        }

        @Test
        void 이미_등록된_심볼은_중복_등록되지_않는다() throws Exception {
            // given
            given(streamClient.createConsumerGroup(anyString())).willReturn(Mono.empty());
            given(streamClient.subscribe(anyString(), anyString())).willReturn(Flux.never());
            given(workerFactory.create(TEST_SYMBOL)).willReturn(mockWorker);

            // when
            sessionManager.registerSymbol(TEST_SYMBOL);
            sessionManager.registerSymbol(TEST_SYMBOL);

            // then
            then(workerFactory).should(times(1)).create(TEST_SYMBOL);
            Map<String, StockSession> sessions = getSessions();
            assertThat(sessions).hasSize(1);
        }

        @Test
        void Worker_생성_실패_시_구독이_정리되고_예외가_발생한다() throws Exception {
            // given
            AtomicBoolean subscriptionDisposed = new AtomicBoolean(false);

            given(streamClient.createConsumerGroup(anyString())).willReturn(Mono.empty());
            given(streamClient.subscribe(anyString(), anyString()))
                    .willReturn(Flux.<OrderbookStreamMessage>never()
                            .doOnCancel(() -> subscriptionDisposed.set(true)));
            willThrow(new RuntimeException("Worker 생성 실패"))
                    .given(workerFactory).create(TEST_SYMBOL);

            // when & then
            assertThatThrownBy(() -> sessionManager.registerSymbol(TEST_SYMBOL))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Worker 생성 실패");

            Map<String, StockSession> sessions = getSessions();
            assertThat(sessions).isEmpty();
            assertThat(subscriptionDisposed.get()).isTrue();
        }

        @Test
        void 여러_심볼을_등록할_수_있다() throws Exception {
            // given
            String symbol1 = "NVDA";
            String symbol2 = "TSLA";
            MatchingWorker worker1 = mock(MatchingWorker.class);
            MatchingWorker worker2 = mock(MatchingWorker.class);

            given(streamClient.createConsumerGroup(anyString())).willReturn(Mono.empty());
            given(streamClient.subscribe(anyString(), anyString())).willReturn(Flux.never());
            given(workerFactory.create(symbol1)).willReturn(worker1);
            given(workerFactory.create(symbol2)).willReturn(worker2);

            // when
            sessionManager.registerSymbol(symbol1);
            sessionManager.registerSymbol(symbol2);

            // then
            Map<String, StockSession> sessions = getSessions();
            assertThat(sessions).hasSize(2);
            assertThat(sessions).containsKeys(symbol1, symbol2);
        }
    }

    @Nested
    @DisplayName("종목 등록 해제 시")
    class UnregisterSymbol {

        @Test
        void 세션이_정상적으로_해제된다() throws Exception {
            // given
            given(streamClient.createConsumerGroup(anyString())).willReturn(Mono.empty());
            given(streamClient.subscribe(anyString(), anyString())).willReturn(Flux.never());
            given(workerFactory.create(TEST_SYMBOL)).willReturn(mockWorker);
            sessionManager.registerSymbol(TEST_SYMBOL);

            // when
            sessionManager.unregisterSymbol(TEST_SYMBOL);

            // then
            Map<String, StockSession> sessions = getSessions();
            assertThat(sessions).isEmpty();
            then(mockWorker).should().shutdown();
        }

        @Test
        void 등록되지_않은_종목은_예외_없이_무시된다() {
            // given
            String unknownSymbol = "UNKNOWN";

            // when & then (예외 없이 완료)
            sessionManager.unregisterSymbol(unknownSymbol);
        }

        @Test
        void 구독과_Worker가_모두_정리된다() {
            // given
            AtomicBoolean subscriptionDisposed = new AtomicBoolean(false);

            given(streamClient.createConsumerGroup(anyString())).willReturn(Mono.empty());
            given(streamClient.subscribe(anyString(), anyString()))
                    .willReturn(Flux.<OrderbookStreamMessage>never()
                            .doOnCancel(() -> subscriptionDisposed.set(true)));
            given(workerFactory.create(TEST_SYMBOL)).willReturn(mockWorker);
            sessionManager.registerSymbol(TEST_SYMBOL);

            // when
            sessionManager.unregisterSymbol(TEST_SYMBOL);

            // then
            assertThat(subscriptionDisposed.get()).isTrue();
            then(mockWorker).should(times(1)).shutdown();
        }
    }

    @Nested
    @DisplayName("주문 라우팅 시")
    class RouteOrder {

        @Test
        void 등록된_심볼로_주문이_라우팅된다() {
            // given
            given(streamClient.createConsumerGroup(anyString())).willReturn(Mono.empty());
            given(streamClient.subscribe(anyString(), anyString())).willReturn(Flux.never());
            given(workerFactory.create(TEST_SYMBOL)).willReturn(mockWorker);
            sessionManager.registerSymbol(TEST_SYMBOL);

            Order order = createSimpleOrder(TEST_SYMBOL);

            // when
            sessionManager.routeOrder(order);

            // then
            then(mockWorker).should(times(1)).addOrder(order);
        }

        @Test
        void 등록되지_않은_심볼로_주문_시_예외가_발생한다() {
            // given
            Order order = createSimpleOrder("UNKNOWN");

            // when & then
            assertThatThrownBy(() -> sessionManager.routeOrder(order))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("등록되지 않은 종목");
        }
    }

    @Nested
    @DisplayName("전체 종료 시")
    class Shutdown {

        @Test
        void 모든_세션이_종료된다() throws Exception {
            // given
            String symbol1 = "NVDA";
            String symbol2 = "TSLA";
            MatchingWorker worker1 = mock(MatchingWorker.class);
            MatchingWorker worker2 = mock(MatchingWorker.class);

            given(streamClient.createConsumerGroup(anyString())).willReturn(Mono.empty());
            given(streamClient.subscribe(anyString(), anyString())).willReturn(Flux.never());
            given(workerFactory.create(symbol1)).willReturn(worker1);
            given(workerFactory.create(symbol2)).willReturn(worker2);

            sessionManager.registerSymbol(symbol1);
            sessionManager.registerSymbol(symbol2);

            // when
            sessionManager.shutdown();

            // then
            Map<String, StockSession> sessions = getSessions();
            assertThat(sessions).isEmpty();
            then(worker1).should().shutdown();
            then(worker2).should().shutdown();
        }
    }

    @Nested
    @DisplayName("동시성 처리 시")
    class Concurrency {

        @Test
        void 동시에_같은_심볼_등록_시_하나만_유지된다() throws Exception {
            // given
            AtomicInteger workerCreationCount = new AtomicInteger(0);

            given(streamClient.createConsumerGroup(anyString())).willReturn(Mono.empty());
            given(streamClient.subscribe(anyString(), anyString())).willReturn(Flux.never());
            willAnswer(invocation -> {
                int count = workerCreationCount.incrementAndGet();
                if (count == 1) return mockWorker;
                else return mockWorker2;
            }).given(workerFactory).create(TEST_SYMBOL);

            // when
            Thread t1 = new Thread(() -> sessionManager.registerSymbol(TEST_SYMBOL));
            Thread t2 = new Thread(() -> sessionManager.registerSymbol(TEST_SYMBOL));

            t1.start();
            t2.start();
            t1.join();
            t2.join();

            // then
            Map<String, StockSession> sessions = getSessions();
            assertThat(sessions).hasSize(1);
            then(mockWorker2).should(times(1)).shutdown();
        }
    }
}