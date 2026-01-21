## 개요
![image](docs/diagram/ServerComponents.png)
Match API 서버는 주문과 호가 데이터를 그림과 같이 전달받아 체결을 처리합니다.


## 체결 동작 원리
![MatchingWorker.png](docs/diagram/MatchingWorker.png)

- 각 종목은 전용 `MatchingWorker`가 **단일 스레드**로 처리
- Worker는 개별 `OrderQueue`를 소유하며, 해당 Worker만 접근 가능
    - Thread-safe 자료구조를 사용하지 않고, 일반 Queue를 사용하여 성능 향상 기대
- 주문/호가 이벤트는 작업큐에 순차 적재 후 도착 순서대로 체결

## 종목 등록
![RegisterSymbol.png](docs/diagram/RegisterSymbol.png)

- `StockSessionManager`를 통해 종목 등록/해제
- 등록 시: 호가 Stream 구독 시작 + `MatchingWorkerFactory`로 Worker 생성
- 생성된 `Disposable`과 `MatchingWorker`를 Symbol 기준 Map으로 관리
- 이벤트 발생 시 Symbol로 담당 Worker를 찾아 처리 위임