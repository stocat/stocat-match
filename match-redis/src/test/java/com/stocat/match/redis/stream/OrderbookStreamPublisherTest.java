package com.stocat.match.redis.stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocat.match.domain.orderbook.Orderbook;
import com.stocat.match.domain.orderbook.PriceLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.ReactiveStreamOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class OrderbookStreamPublisherTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private ReactiveStreamOperations<String, Object, Object> streamOperations;

    @Mock
    private ObjectMapper objectMapper;

    private RedisStreamProperties properties;

    private OrderbookStreamPublisher publisher;

    private static final String TEST_SYMBOL = "NVDA";
    private static final String KEY_PREFIX = "orderbook:stream:";

    @BeforeEach
    void setUp() {
        properties = new RedisStreamProperties(KEY_PREFIX, "matching-engine", null, 10);
        publisher = new OrderbookStreamPublisher(redisTemplate, properties, objectMapper);
    }

    private Orderbook createOrderbook(String symbol) {
        return new Orderbook(
                symbol,
                List.of(
                        new PriceLevel(BigDecimal.valueOf(50100), BigDecimal.valueOf(100)),
                        new PriceLevel(BigDecimal.valueOf(50200), BigDecimal.valueOf(200))
                ),
                List.of(
                        new PriceLevel(BigDecimal.valueOf(50000), BigDecimal.valueOf(150)),
                        new PriceLevel(BigDecimal.valueOf(49900), BigDecimal.valueOf(250))
                )
        );
    }

    @Nested
    @DisplayName("호가 발행 시")
    class PublishOrderbook {

        @Test
        @SuppressWarnings("unchecked")
        void 호가_데이터가_정상적으로_Stream에_발행된다() throws Exception {
            // given
            Orderbook orderbook = createOrderbook(TEST_SYMBOL);
            RecordId expectedRecordId = RecordId.of("1234567890-0");

            given(objectMapper.writeValueAsString(orderbook)).willReturn("orderbook json");
            given(redisTemplate.opsForStream()).willReturn(streamOperations);
            given(streamOperations.add(any(ObjectRecord.class))).willReturn(Mono.just(expectedRecordId));

            // when
            Mono<RecordId> result = publisher.publish(orderbook);

            // then
            StepVerifier.create(result)
                    .expectNext(expectedRecordId)
                    .verifyComplete();

            then(objectMapper).should().writeValueAsString(orderbook);
            then(redisTemplate).should().opsForStream();
        }

        @Test
        @SuppressWarnings("unchecked")
        void 발행된_Record에_올바른_필드가_포함된다() throws Exception {
            // given
            Orderbook orderbook = createOrderbook(TEST_SYMBOL);
            String expectedJson = "{\"symbol\":\"005930\"}";
            String expectedStreamKey = KEY_PREFIX + TEST_SYMBOL;
            RecordId expectedRecordId = RecordId.of("1234567890-0");

            given(objectMapper.writeValueAsString(orderbook)).willReturn(expectedJson);
            given(redisTemplate.opsForStream()).willReturn(streamOperations);

            ArgumentCaptor<ObjectRecord<String, Map<String, String>>> recordCaptor =
                    ArgumentCaptor.forClass(ObjectRecord.class);
            given(streamOperations.add(recordCaptor.capture())).willReturn(Mono.just(expectedRecordId));

            // when
            publisher.publish(orderbook).block();

            // then
            ObjectRecord<String, Map<String, String>> capturedRecord = recordCaptor.getValue();
            assertThat(capturedRecord.getStream()).isEqualTo(KEY_PREFIX + TEST_SYMBOL);

            Map<String, String> value = capturedRecord.getValue();
            assertThat(capturedRecord.getStream()).isEqualTo(expectedStreamKey);
            assertThat(value).containsKey("symbol");
            assertThat(value).containsKey("data");
            assertThat(value.get("symbol")).isEqualTo(TEST_SYMBOL);
            assertThat(value.get("data")).isEqualTo(expectedJson);
        }

        @Test
        void ObjectMapper_직렬화_실패_시_에러가_전파된다() throws Exception {
            // given
            Orderbook orderbook = createOrderbook(TEST_SYMBOL);
            JsonProcessingException expectedException = new JsonProcessingException("직렬화 실패") {};

            given(objectMapper.writeValueAsString(orderbook)).willThrow(expectedException);

            // when
            Mono<RecordId> result = publisher.publish(orderbook);

            // then
            StepVerifier.create(result)
                    .expectError(JsonProcessingException.class)
                    .verify();
        }
    }
}