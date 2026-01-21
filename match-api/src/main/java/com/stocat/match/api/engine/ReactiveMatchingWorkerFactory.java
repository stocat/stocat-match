package com.stocat.match.api.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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

    @Override
    public MatchingWorker create(String symbol) {
        log.debug("ReactiveSymbolMatchingWorker 생성: symbol={}", symbol);
        return new ReactiveSymbolMatchingWorker(symbol, matchingEngine);
    }
}