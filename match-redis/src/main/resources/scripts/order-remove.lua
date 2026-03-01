    -- 주문 삭제: HGET(quantity) + DEL(CAS) + ZREM 원자적 수행
-- KEYS[1] = hashKey (e.g. order:detail:42)
-- KEYS[2] = zsetKey (e.g. order:buy:NVDA)
-- ARGV[1] = member (e.g. 000001735689600000|42)

-- 삭제 전 잔여 수량 조회
local quantity = redis.call('HGET', KEYS[1], 'quantity')
if not quantity then
    return nil
end

-- Hash 삭제 (CAS 역할: 이미 삭제된 경우 0 반환)
local deleted = redis.call('DEL', KEYS[1])
if deleted == 0 then
    return nil
end

-- ZSET에서 member 제거
redis.call('ZREM', KEYS[2], ARGV[1])

return quantity