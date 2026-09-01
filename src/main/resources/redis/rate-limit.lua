-- Token bucket chay nguyen tu tren Redis.
--
-- Vi sao khong dung bien dem trong JVM: bien do chi song trong MOT instance.
-- Deploy hai instance la gioi han bi nhan doi ngay. Trang thai rate limit phai
-- nam o tang dung chung - giong het ly do khong dung 'synchronized' de chong oversell.
--
-- KEYS[1] = rl:{scope}:{identity}
-- ARGV[1] = capacity          suc chua toi da cua gau
-- ARGV[2] = refillPerSecond   so token nap lai moi giay
-- ARGV[3] = nowMillis
-- ARGV[4] = requested
--
-- Tra ve: { allowed (1/0), remaining, retryAfterMillis }

local capacity  = tonumber(ARGV[1])
local refill    = tonumber(ARGV[2])
local now       = tonumber(ARGV[3])
local requested = tonumber(ARGV[4])

local bucket = redis.call('HMGET', KEYS[1], 'tokens', 'ts')
local tokens = tonumber(bucket[1])
local ts     = tonumber(bucket[2])

if tokens == nil then
    tokens = capacity
    ts = now
end

-- Nap lai theo thoi gian troi qua, khong bao gio vuot suc chua
local elapsed = math.max(0, now - ts)
tokens = math.min(capacity, tokens + (elapsed / 1000.0) * refill)

local allowed = 0
local retryAfter = 0

if tokens >= requested then
    tokens = tokens - requested
    allowed = 1
else
    -- Con thieu bao nhieu token thi cho bay nhieu
    retryAfter = math.ceil(((requested - tokens) / refill) * 1000)
end

redis.call('HSET', KEYS[1], 'tokens', tokens, 'ts', now)
-- Gau day lai hoan toan sau capacity/refill giay; het han thi xoa cho nhe Redis
redis.call('EXPIRE', KEYS[1], math.ceil(capacity / refill) + 1)

return { allowed, math.floor(tokens), retryAfter }
