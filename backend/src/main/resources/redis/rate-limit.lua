-- Token bucket chạy nguyên tử trên Redis.
--
-- Vì sao không dùng biến đếm trong JVM: biến đó chỉ sống trong MỘT instance.
-- Deploy hai instance là giới hạn bị nhân đôi ngay. Trạng thái rate limit phải
-- nằm ở tầng dùng chung - giống hệt lý do không dùng 'synchronized' để chống oversell.
--
-- KEYS[1] = rl:{scope}:{identity}
-- ARGV[1] = capacity          sức chứa tối đa của gầu
-- ARGV[2] = refillPerSecond   số token nạp lại mỗi giây
-- ARGV[3] = nowMillis
-- ARGV[4] = requested
--
-- Trả về: { allowed (1/0), remaining, retryAfterMillis }

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

-- Nạp lại theo thời gian trôi qua, không bao giờ vượt sức chứa
local elapsed = math.max(0, now - ts)
tokens = math.min(capacity, tokens + (elapsed / 1000.0) * refill)

local allowed = 0
local retryAfter = 0

if tokens >= requested then
    tokens = tokens - requested
    allowed = 1
else
    -- Còn thiếu bao nhiêu token thì cho bấy nhiêu
    retryAfter = math.ceil(((requested - tokens) / refill) * 1000)
end

redis.call('HSET', KEYS[1], 'tokens', tokens, 'ts', now)
-- Gầu đầy lại hoàn toàn sau capacity/refill giây; hết hạn thì xoá cho nhẹ Redis
redis.call('EXPIRE', KEYS[1], math.ceil(capacity / refill) + 1)

return { allowed, math.floor(tokens), retryAfter }
