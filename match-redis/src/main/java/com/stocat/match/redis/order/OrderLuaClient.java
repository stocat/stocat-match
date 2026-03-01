package com.stocat.match.redis.order;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lua 스크립트 기반 원자적 주문 추가/삭제
 * - enqueue: ZADD + HSET 원자적 수행
 * - remove: DEL(CAS) + ZREM 원자적 수행
 */
@Component
public class OrderLuaClient {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final RedisScript<Long> addScript;
    private final RedisScript<String> removeScript;

    public OrderLuaClient(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.addScript = RedisScript.of(new ClassPathResource("scripts/order-add.lua"), Long.class);
        this.removeScript = RedisScript.of(new ClassPathResource("scripts/order-remove.lua"), String.class);
    }

    /**
     * ZADD + HSET를 원자적으로 수행한다.
     *
     * @param zsetKey ZSET 키 (e.g. order:buy:NVDA)
     * @param hashKey HASH 키 (e.g. order:detail:42)
     * @param score   ZSET score
     * @param member  ZSET member
     * @param fields  HASH 필드-값 맵
     */
    public Mono<Void> enqueue(String zsetKey, String hashKey,
                               double score, String member,
                               Map<String, String> fields) {
        List<String> keys = List.of(zsetKey, hashKey);
        List<String> args = buildAddArgs(score, member, fields);

        return redisTemplate.execute(addScript, keys, args)
                .then();
    }

    /**
     * HGET(quantity) + DEL(CAS) + ZREM을 원자적으로 수행한다.
     * 삭제 직전의 잔여 수량을 조회한 뒤 삭제하므로, 부분 체결 후에도 정확한 취소 수량을 보장한다.
     *
     * @param hashKey HASH 키 (e.g. order:detail:42)
     * @param zsetKey ZSET 키 (e.g. order:buy:NVDA)
     * @param member  ZSET member
     * @return 취소된 수량 (이미 삭제된 경우 empty)
     */
    public Mono<BigDecimal> remove(String hashKey, String zsetKey, String member) {
        List<String> keys = List.of(hashKey, zsetKey);

        return redisTemplate.execute(removeScript, keys, List.of(member))
                .next()
                .map(BigDecimal::new);
    }

    private List<String> buildAddArgs(double score, String member, Map<String, String> fields) {
        List<String> args = new ArrayList<>(2 + fields.size() * 2);
        args.add(String.valueOf(score));
        args.add(member);

        for (Map.Entry<String, String> entry : fields.entrySet()) {
            args.add(entry.getKey());
            args.add(entry.getValue());
        }
        return args;
    }
}