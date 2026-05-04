-- 滑动窗口高频交易检测 Lua 脚本 (原子性)
-- KEYS[1]: aml:velocity:{customerId}  (ZSET)
-- ARGV[1]: 窗口起始时间戳 (毫秒), 即 now - windowSeconds*1000
-- ARGV[2]: 当前时间戳 (毫秒)
-- ARGV[3]: 阈值 (最大允许笔数)
-- ARGV[4]: 成员唯一标识 (transactionId 或 UUID, 用于ZADD去重)
-- ARGV[5]: key过期秒数 (TTL)
--
-- 返回: {当前窗口内笔数, 是否触发 (1/0)}

local key = KEYS[1]
local windowStart = tonumber(ARGV[1])
local now = tonumber(ARGV[2])
local threshold = tonumber(ARGV[3])
local member = ARGV[4]
local ttl = tonumber(ARGV[5])

-- 1. 清理滑动窗口外的过期成员
redis.call('ZREMRANGEBYSCORE', key, '-inf', windowStart)

-- 2. 添加当前交易时间戳
redis.call('ZADD', key, now, member)

-- 3. 设置key过期时间(兜底清理)
redis.call('EXPIRE', key, ttl)

-- 4. 统计当前窗口内的交易笔数
local count = redis.call('ZCARD', key)

-- 5. 判断是否触发阈值
local triggered = 0
if count >= threshold then
    triggered = 1
end

return {count, triggered}
