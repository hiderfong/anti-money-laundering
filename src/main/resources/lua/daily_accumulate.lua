-- 日累计金额统计 Lua 脚本 (原子性)
-- KEYS[1]: aml:daily:{customerId}:{date}  (HASH)
-- ARGV[1]: 本次交易金额 (分/最小单位, 用整数避免浮点问题, 即 amount * 100)
-- ARGV[2]: 日累计金额阈值 (分/最小单位)
-- ARGV[3]: key过期秒数 (TTL, 约2天)
--
-- 返回: {总累计金额(分), 交易笔数, 是否触发 (1/0)}

local key = KEYS[1]
local amountCent = tonumber(ARGV[1])
local thresholdCent = tonumber(ARGV[2])
local ttl = tonumber(ARGV[3])

-- 1. 原子递增累计金额
local totalAmount = redis.call('HINCRBY', key, 'total_amount', amountCent)

-- 2. 原子递增交易笔数
local txnCount = redis.call('HINCRBY', key, 'txn_count', 1)

-- 3. 设置key过期时间(当天有效 + 1天缓冲)
redis.call('EXPIRE', key, ttl)

-- 4. 判断是否超过阈值
local triggered = 0
if totalAmount >= thresholdCent then
    triggered = 1
end

return {totalAmount, txnCount, triggered}
