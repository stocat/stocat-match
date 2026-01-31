# stocat-match

`stocat-match`는 실제 호가 및 수량을 기반으로 가상 매매를 체결하는 시스템입니다.

## 주요 기능
- 실시간 호가를 바탕으로 주문 체결
- 체결 시 호가와 주문의 순서 보장

## 기술 스택
- Java 21
- Spring Boot 3.5.x
- Gradle 8.x (Wrapper 포함)
- Reactor (Webflux)
- Redis Stream (Kafka 대체 가능)

## 프로젝트 구조
```
src/
└─ main/
   ├─ java/com/stocat/match/
   │  ├─ config/
   │  ├─ controller/
   │  ├─ domain/
   │  ├─ repository/
   │  ├─ service/
   │  └─ StocatApplication.java
   └─ resources/
      └─ application.yml
```

## 빠른 시작
```bash
# 프로젝트 클론
git clone https://github.com/your-username/stocat-backend.git
cd stocat-backend

# 환경 설정: src/main/resources/application.yml 수정
# JWT 시크릿, DB URL 등

# 실행 (Unix/macOS)
./gradlew bootRun

# 실행 (Windows)
gradlew.bat bootRun
```