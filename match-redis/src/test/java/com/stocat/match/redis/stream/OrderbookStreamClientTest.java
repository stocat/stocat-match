package com.stocat.match.redis.stream;

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
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.ReactiveStreamOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class OrderbookStreamClientTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private ReactiveStreamOperations<String, Object, Object> streamOperations;

    private ObjectMapper objectMapper;

    private RedisStreamProperties properties;

    private OrderbookStreamClient client;

    private static final String TEST_SYMBOL = "NVDA";
    private static final String KEY_PREFIX = "orderbook:stream:";
    private static final String CONSUMER_GROUP = "matching-engine";
    private static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(5);
    private static final int BATCH_SIZE = 10;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        properties = new RedisStreamProperties(KEY_PREFIX, CONSUMER_GROUP, BLOCK_TIMEOUT, BATCH_SIZE);
        client = new OrderbookStreamClient(redisTemplate, properties, objectMapper);
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
    @DisplayName("Consumer Group 생성 시")
    class CreateConsumerGroup {

        @Test
        void Consumer_Group이_정상적으로_생성된다() {
            // given
            String expectedStreamKey = KEY_PREFIX + TEST_SYMBOL;
            given(redisTemplate.opsForStream()).willReturn(streamOperations);
            given(streamOperations.createGroup(
                    eq(expectedStreamKey),
                    any(ReadOffset.class),
                    eq(CONSUMER_GROUP)
            )).willReturn(Mono.just("OK"));

            // when
            Mono<Void> result = client.createConsumerGroup(TEST_SYMBOL);

            // then
            StepVerifier.create(result)
                    .verifyComplete();

            then(streamOperations).should().createGroup(
                    eq(expectedStreamKey),
                    any(ReadOffset.class),
                    eq(CONSUMER_GROUP)
            );
        }

        @Test
        void 이미_존재하는_그룹이면_에러_없이_완료된다() {
            // given
            String expectedStreamKey = KEY_PREFIX + TEST_SYMBOL;
            RuntimeException busyGroupException = new RuntimeException("BUSYGROUP");

            given(redisTemplate.opsForStream()).willReturn(streamOperations);
            given(streamOperations.createGroup(
                    eq(expectedStreamKey),
                    any(ReadOffset.class),
                    eq(CONSUMER_GROUP)
            )).willReturn(Mono.error(busyGroupException));

            // when
            Mono<Void> result = client.createConsumerGroup(TEST_SYMBOL);

            // then
            StepVerifier.create(result)
                    .verifyComplete();
        }

        @Test
        void BUSYGROUP_외_에러는_전파된다() {
            // given
            String expectedStreamKey = KEY_PREFIX + TEST_SYMBOL;
            RuntimeException otherException = new RuntimeException("Connection refused");

            given(redisTemplate.opsForStream()).willReturn(streamOperations);
            given(streamOperations.createGroup(
                    eq(expectedStreamKey),
                    any(ReadOffset.class),
                    eq(CONSUMER_GROUP)
            )).willReturn(Mono.error(otherException));

            // when
            Mono<Void> result = client.createConsumerGroup(TEST_SYMBOL);

            // then
            StepVerifier.create(result)
                    .expectErrorMatches(e -> e instanceof RuntimeException
                            && e.getMessage().equals("Connection refused"))
                    .verify();
        }
    }

    @Nested
    @DisplayName("Stream 구독 시")
    class Subscribe {

        @Test
        @SuppressWarnings("unchecked")
        void 메시지를_정상적으로_수신한다() throws Exception {
            // given
            String consumerName = "consumer-1";
            String recordId = "1234567890-0";
            Orderbook expectedOrderbook = createOrderbook(TEST_SYMBOL);
            String orderbookJson = objectMapper.writeValueAsString(expectedOrderbook);


            MapRecord<String, String, String> record = MapRecord.create(
                    KEY_PREFIX + TEST_SYMBOL,
                    Map.of(
                            "symbol", TEST_SYMBOL,
                            "data", orderbookJson
                    )
            ).withId(RecordId.of(recordId));

            given(redisTemplate.opsForStream()).willReturn(streamOperations);
            given(streamOperations.read(
                    any(Consumer.class),
                    any(StreamReadOptions.class),
                    any(StreamOffset.class)
            )).willReturn(Flux.just(record), Flux.never());

            // when
            Flux<OrderbookStreamMessage> result = client.subscribe(TEST_SYMBOL, consumerName);

            // then
            StepVerifier.create(result.take(1))
                    .assertNext(message -> {
                        assertThat(message.recordId()).isEqualTo(recordId);
                        assertThat(message.symbol()).isEqualTo(TEST_SYMBOL);
                        assertThat(message.orderbook()).isEqualTo(expectedOrderbook);
                    })
                    .verifyComplete();
        }

        @Test
        @SuppressWarnings("unchecked")
        void 올바른_Consumer와_옵션으로_구독한다() throws Exception {
            // given
            String consumerName = "consumer-1";
            String recordId = "1234567890-0";
            Orderbook expectedOrderbook = createOrderbook(TEST_SYMBOL);
            String orderbookJson = objectMapper.writeValueAsString(expectedOrderbook);

            MapRecord<String, String, String> record = MapRecord.create(
                    KEY_PREFIX + TEST_SYMBOL,
                    Map.of("symbol", TEST_SYMBOL, "data", orderbookJson)
            ).withId(RecordId.of(recordId));

            ArgumentCaptor<Consumer> consumerCaptor = ArgumentCaptor.forClass(Consumer.class);
            ArgumentCaptor<StreamReadOptions> optionsCaptor = ArgumentCaptor.forClass(StreamReadOptions.class);
            ArgumentCaptor<StreamOffset> offsetCaptor = ArgumentCaptor.forClass(StreamOffset.class);

            given(redisTemplate.opsForStream()).willReturn(streamOperations);
            given(streamOperations.read(
                    consumerCaptor.capture(),
                    optionsCaptor.capture(),
                    offsetCaptor.capture()
            )).willReturn(Flux.just(record), Flux.never());

            // when
            client.subscribe(TEST_SYMBOL, consumerName).take(1).blockLast();

            // then
            Consumer capturedConsumer = consumerCaptor.getValue();
            assertThat(capturedConsumer.getGroup()).isEqualTo(CONSUMER_GROUP);
            assertThat(capturedConsumer.getName()).isEqualTo(consumerName);

            StreamOffset<String> capturedOffset = offsetCaptor.getValue();
            assertThat(capturedOffset.getKey()).isEqualTo(KEY_PREFIX + TEST_SYMBOL);
        }
    }

    @Nested
    @DisplayName("메시지 확인(ACK) 시")
    class Acknowledge {

        @Test
        void 메시지가_정상적으로_확인된다() {
            // given
            String recordId = "1234567890-0";
            String expectedStreamKey = KEY_PREFIX + TEST_SYMBOL;

            given(redisTemplate.opsForStream()).willReturn(streamOperations);
            given(streamOperations.acknowledge(expectedStreamKey, CONSUMER_GROUP, recordId))
                    .willReturn(Mono.just(1L));

            // when
            Mono<Long> result = client.acknowledge(TEST_SYMBOL, recordId);

            // then
            StepVerifier.create(result)
                    .expectNext(1L)
                    .verifyComplete();

            then(streamOperations).should().acknowledge(expectedStreamKey, CONSUMER_GROUP, recordId);
        }

        @Test
        void ACK_실패_시_에러가_전파된다() {
            // given
            String recordId = "1234567890-0";
            String expectedStreamKey = KEY_PREFIX + TEST_SYMBOL;
            RuntimeException expectedException = new RuntimeException("Redis connection failed");

            given(redisTemplate.opsForStream()).willReturn(streamOperations);
            given(streamOperations.acknowledge(expectedStreamKey, CONSUMER_GROUP, recordId))
                    .willReturn(Mono.error(expectedException));

            // when
            Mono<Long> result = client.acknowledge(TEST_SYMBOL, recordId);

            // then
            StepVerifier.create(result)
                    .expectErrorMatches(e -> e instanceof RuntimeException
                            && e.getMessage().equals("Redis connection failed"))
                    .verify();
        }
    }

    @Nested
    @DisplayName("Pending 메시지 조회 시")
    class ReadPending {

        @Test
        @SuppressWarnings("unchecked")
        void Pending_메시지를_정상적으로_조회한다() throws Exception {
            // given
            String groupName = "test-group";
            String consumerName = "consumer-1";
            long count = 5;
            String recordId = "1234567890-0";
            String orderbookJson = objectMapper.writeValueAsString(createOrderbook(TEST_SYMBOL));
            Orderbook expectedOrderbook = createOrderbook(TEST_SYMBOL);

            MapRecord<String, String, String> record = MapRecord.create(
                    KEY_PREFIX + TEST_SYMBOL,
                    Map.of(
                            "symbol", TEST_SYMBOL,
                            "data", orderbookJson
                    )
            ).withId(RecordId.of(recordId));

            given(redisTemplate.opsForStream()).willReturn(streamOperations);
            given(streamOperations.read(
                    any(Consumer.class),
                    any(StreamReadOptions.class),
                    any(StreamOffset.class)
            )).willReturn(Flux.just(record));

            // when
            Flux<OrderbookStreamMessage> result = client.readPending(TEST_SYMBOL, groupName, consumerName, count);

            // then
            StepVerifier.create(result)
                    .assertNext(message -> {
                        assertThat(message.recordId()).isEqualTo(recordId);
                        assertThat(message.symbol()).isEqualTo(TEST_SYMBOL);
                        assertThat(message.orderbook()).isEqualTo(expectedOrderbook);
                    })
                    .verifyComplete();
        }

        @Test
        @SuppressWarnings("unchecked")
        void Pending_메시지가_없으면_빈_Flux를_반환한다() {
            // given
            String groupName = "test-group";
            String consumerName = "consumer-1";
            long count = 5;

            given(redisTemplate.opsForStream()).willReturn(streamOperations);
            given(streamOperations.read(
                    any(Consumer.class),
                    any(StreamReadOptions.class),
                    any(StreamOffset.class)
            )).willReturn(Flux.empty());

            // when
            Flux<OrderbookStreamMessage> result = client.readPending(TEST_SYMBOL, groupName, consumerName, count);

            // then
            StepVerifier.create(result)
                    .expectNextCount(0)
                    .verifyComplete();
        }
    }
}