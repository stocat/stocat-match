package com.stocat.match.api.engine;

import com.stocat.match.domain.order.Order;
import com.stocat.match.domain.orderbook.Orderbook;
import com.stocat.match.redis.stream.OrderbookStreamClient;
import com.stocat.match.redis.stream.OrderbookStreamMessage;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * - 종목별 세션 생명주기 관리 (Worker + Redis Stream 구독)
 * - 주문/호가처리 Task 라우팅
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockSessionManager {

    private static final String CONSUMER_NAME_PREFIX = "consumer-";

    private final MatchingWorkerFactory workerFactory;
    private final OrderbookStreamClient streamClient;

    private final Map<String, StockSession> sessions = new ConcurrentHashMap<>();

    /**
     * 종목 세션 시작
     * - Worker 생성
     * - Redis Stream 구독 시작
     */
    public void registerSymbol(String symbol) {
        sessions.computeIfAbsent(symbol, key -> {
            Disposable subscription = consumeOrderbook(key)
                    .subscribe(
                            orderbook -> log.debug("호가 처리 완료: symbol={}", key),
                            error -> log.error("호가 처리 오류: symbol={}, error={}", key, error.getMessage())
                    );
            MatchingWorker worker = workerFactory.create(key);

            return new StockSession(key, worker, subscription);
        });
    }

    /**
     * 종목 세션 종료
     */
    public void unregisterSymbol(String symbol) {
        StockSession session = sessions.remove(symbol);
        if (session == null) {
            return;
        }

        session.dispose();
    }

    // === 라우팅 ===

    /**
     * 주문 라우팅
     * - 해당 종목의 Worker에게 전달
     */
    public void routeOrder(Order order) {
        StockSession session = sessions.get(order.symbol());
        if (session == null) {
            throw new IllegalStateException("등록되지 않은 종목: " + order.symbol());
        }
        session.worker().addOrder(order);
    }


    /**
     * Redis Stream에서 호가 구독
     */
    private Flux<Orderbook> consumeOrderbook(String symbol) {
        String consumerName = CONSUMER_NAME_PREFIX + symbol;

        return streamClient.createConsumerGroup(symbol)
                .thenMany(streamClient.subscribe(symbol, consumerName))
                .flatMap(message -> processMessage(message, symbol))
                .doOnSubscribe(s -> log.info("호가 구독 시작: symbol={}", symbol))
                .doOnCancel(() -> log.info("호가 구독 취소: symbol={}", symbol))
                .doOnError(e -> log.error("호가 구독 오류: symbol={}, error={}", symbol, e.getMessage()));
    }

    /**
     * 메시지 처리 및 ACK
     */
    private Mono<Orderbook> processMessage(OrderbookStreamMessage message, String symbol) {
        routeOrderbook(message.orderbook());
        return streamClient.acknowledge(symbol, message.recordId())
                .thenReturn(message.orderbook());
    }

    /**
     * 호가 라우팅
     * - 해당 종목의 Worker에게 전달
     */
    private void routeOrderbook(Orderbook orderbook) {
        StockSession session = sessions.get(orderbook.symbol());
        if (session == null) {
            throw new IllegalStateException("등록되지 않은 종목: " + orderbook.symbol());
        }
        session.worker().processOrderbook(orderbook);
    }

    /**
     * 모든 세션 종료
     */
    @PreDestroy
    public void shutdown() {
        sessions.values().forEach(StockSession::dispose);
        sessions.clear();
    }
}