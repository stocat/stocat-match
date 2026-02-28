-- 주문 추가: ZADD + HSET 원자적 수행
-- KEYS[1] = zsetKey (e.g. order:buy:NVDA)
-- KEYS[2] = hashKey (e.g. order:detail:42)
-- ARGV[1] = score
-- ARGV[2] = member
-- ARGV 이후 = hash field-value 쌍 (field1, value1, field2, value2, ...)

redis.call('ZADD', KEYS[1], ARGV[1], ARGV[2])

local hashArgs = {}
for i = 3, #ARGV, 2 do
    hashArgs[#hashArgs + 1] = ARGV[i]
    hashArgs[#hashArgs + 1] = ARGV[i + 1]
end

redis.call('HSET', KEYS[2], unpack(hashArgs))

return 1