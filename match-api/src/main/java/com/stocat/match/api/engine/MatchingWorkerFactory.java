package com.stocat.match.api.engine;

/**
 * MatchingWorker 생성 팩토리 인터페이스
 * - Worker 생성 책임 분리
 * - 구현체: ExecutorsMatchingWorkerFactory, ReactiveMatchingWorkerFactory
 */
public interface MatchingWorkerFactory {

    /**
     * 종목별 MatchingWorker 생성
     *
     * @param symbol 종목 코드
     * @return 생성된 MatchingWorker
     */
    MatchingWorker create(String symbol);
}