package com.stocat.match.api.engine;

import com.stocat.match.api.infrastructure.trade.TradeApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * Reactor 기반 MatchingWorker 팩토리
 * - 종목별 단일 스레드 Scheduler로 순서 보장
 * - 이벤트 기반 비동기 처리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReactiveMatchingWorkerFactory implements MatchingWorkerFactory {

    private final MatchingEngine matchingEngine;
    private final TradeApiClient tradeApiClient;

    @Override
    public MatchingWorker create(String symbol) {
        Scheduler scheduler = Schedulers.newSingle("reactive-matching-" + symbol);
        return new ReactiveSymbolMatchingWorker(symbol, matchingEngine, tradeApiClient, scheduler);
    }
}