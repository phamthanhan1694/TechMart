-- Tru ton kho flash sale trong MOT lenh nguyen tu.
--
-- Vi sao phai la Lua chu khong phai DECR roi kiem tra:
--   DECR -> thay am -> INCR bu lai  la mot chuoi BA lenh khong nguyen tu.
--   Giua chung co request khac chen vao va doc phai ton kho am.
--   Lua chay tren Redis nhu mot lenh duy nhat, khong co khe ho.
--
-- KEYS[1] = fs:{itemId}:stock        bo dem ton kho cua suat ban
-- KEYS[2] = fs:{itemId}:user:{uid}   so luong user nay da mua
-- ARGV[1] = so luong muon mua
-- ARGV[2] = gioi han moi user
-- ARGV[3] = TTL (giay) cho bo dem theo user
--
-- Tra ve:
--   >= 0  ton kho con lai sau khi tru (mua thanh cong)
--   -1    het suat
--   -2    vuot gioi han moi user
--   -3    chua warmup (khong co key ton kho) -> fail-closed, tu choi ban

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
