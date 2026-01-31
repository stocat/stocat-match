package com.stocat.match.api.infrastructure.trade;

import com.stocat.match.domain.fill.Fill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;

@Slf4j
@Component
public class TradeApiClient {

    private final WebClient webClient;

    public TradeApiClient(
            WebClient.Builder webClientBuilder,
            @Value("${api.trade.base-url}") String baseUrl
    ) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
    }

    /**
     * 체결 결과 전송 (비동기, 재시도 포함)
     */
    public void sendFill(Fill fill) {
        webClient.post()
                .uri("/internal/fills")
                .bodyValue(fill)
                .retrieve()
                .bodyToMono(Void.class)
                .retryWhen(Retry.backoff(3, Duration.ofMillis(100))
                        .maxBackoff(Duration.ofSeconds(2)))
                .doOnError(e -> log.error("체결 전송 최종 실패: fill={}, error={}", fill, e.getMessage()))
                .subscribe();
    }
}