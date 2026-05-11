-- 滑动窗口限流 Lua 脚本
-- KEYS[1]: 限流 key
-- ARGV[1]: 窗口大小（毫秒）
-- ARGV[2]: 最大请求数
-- ARGV[3]: 当前时间戳（毫秒）
-- 返回: {allowed(1/0), remaining, retryAfter_ms}

local key = KEYS[1]
local window = tonumber(ARGV[1])
local maxRequests = tonumber(ARGV[2])
local now = tonumber(ARGV[3])

-- 移除窗口外的过期成员
redis.call('ZREMRANGEBYSCORE', key, 0, now - window)

-- 获取当前窗口内的请求数
local current = redis.call('ZCARD', key)

if current < maxRequests then
    -- 未超限，添加当前请求
    redis.call('ZADD', key, now, now .. ':' .. math.random(1000000))
    redis.call('PEXPIRE', key, window)
    return {1, maxRequests - current - 1, 0}
else
    -- 已超限，计算重试时间
    local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
    local retryAfter = 0
    if #oldest > 0 then
        retryAfter = tonumber(oldest[2]) + window - now
        if retryAfter < 0 then retryAfter = 0 end
    end
    return {0, 0, retryAfter}
end
