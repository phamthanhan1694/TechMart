-- Đếm lần đăng nhập sai theo tài khoản; đủ ngưỡng thì dựng cờ khoá tạm thời.
--
-- KEYS[1] = bộ đếm lần sai     KEYS[2] = cờ khoá
-- ARGV[1] = cửa sổ đếm (ms)     ARGV[2] = số lần sai tối đa     ARGV[3] = thời gian khoá (ms)
--
-- Trả về: số lần sai hiện tại, hoặc -1 nếu lần này vừa làm tài khoản bị khoá.
--
-- Phải là một script chứ không phải INCR rồi EXPIRE từ Java: app chết giữa hai lệnh thì
-- bộ đếm không có TTL và tồn tại vĩnh viễn - tài khoản bị khoá ngay khi sai lần kế tiếp,
-- dù lần sai trước đó đã từ tuần trước.

local count = redis.call('INCR', KEYS[1])
if count == 1 then
    redis.call('PEXPIRE', KEYS[1], ARGV[1])
end

if count >= tonumber(ARGV[2]) then
    redis.call('SET', KEYS[2], '1', 'PX', ARGV[3])
    redis.call('DEL', KEYS[1])
    return -1
end
return count
