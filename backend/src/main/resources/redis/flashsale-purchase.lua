-- Trừ tồn kho flash sale trong MỘT lệnh nguyên tử.
--
-- Vì sao phải là Lua chứ không phải DECR rồi kiểm tra:
--   DECR -> thấy âm -> INCR bù lại  là một chuỗi BA lệnh không nguyên tử.
--   Giữa chừng có request khác chen vào và đọc phải tồn kho âm.
--   Lua chạy trên Redis như một lệnh duy nhất, không có khe hở.
--
-- KEYS[1] = fs:{itemId}:stock        bộ đếm tồn kho của suất bán
-- KEYS[2] = fs:{itemId}:user:{uid}   số lượng user này đã mua
-- ARGV[1] = số lượng muốn mua
-- ARGV[2] = giới hạn mỗi user
-- ARGV[3] = TTL (giây) cho bộ đếm theo user
--
-- Trả về:
--   >= 0  tồn kho còn lại sau khi trừ (mua thành công)
--   -1    hết suất
--   -2    vượt giới hạn mỗi user
--   -3    chưa warmup (không có key tồn kho) -> fail-closed, từ chối bán

local stockRaw = redis.call('GET', KEYS[1])
if not stockRaw then
    return -3
end

local stock    = tonumber(stockRaw)
local quantity = tonumber(ARGV[1])
local perUser  = tonumber(ARGV[2])
local ttl      = tonumber(ARGV[3])

local bought = tonumber(redis.call('GET', KEYS[2]) or '0')
if bought + quantity > perUser then
    return -2
end

if stock < quantity then
    return -1
end

redis.call('DECRBY', KEYS[1], quantity)
redis.call('INCRBY', KEYS[2], quantity)
redis.call('EXPIRE', KEYS[2], ttl)

return stock - quantity
