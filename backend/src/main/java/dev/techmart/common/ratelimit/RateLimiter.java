package dev.techmart.common.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;

/**
 * Token bucket dùng chung trên Redis.
 *
 * <p><b>Vì sao không đếm trong bộ nhớ JVM:</b> biến đếm đó chỉ sống trong một instance.
 * Deploy hai instance là giới hạn bị nhân đôi ngay. Trạng thái rate limit phải nằm ở tầng
 * dùng chung - cùng một lý do khiến {@code synchronized} vô dụng khi chống oversell.
 *
 * <p><b>Fail-open khi Redis chết.</b> Chặn nhầm toàn bộ người dùng thật tệ hơn nhiều so với
 * tạm thời mất khả năng chống bot. Ngược với flash sale (fail-closed) - xem
 * {@code docs/04-error-catalog.md} mục 9.
 *
 * <p>Viết bằng Lua thay vì dùng thư viện (Bucket4j) vì flash sale đã cần dùng cơ chế
 * Redis + Lua này rồi: một khái niệm cho cả hai tính năng, và không thêm phụ thuộc.
 */
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private final StringRedisTemplate redis;
    @SuppressWarnings("rawtypes")
    private final RedisScript<List> script;
    private final Clock clock;

    @SuppressWarnings("rawtypes")
    public RateLimiter(StringRedisTemplate redis, RedisScript<List> rateLimitScript, Clock clock) {
        this.redis = redis;
        this.script = rateLimitScript;
        this.clock = clock;
    }

    /**
     * @param key      định danh gầu, vd {@code rl:login:203.0.113.7}
     * @param capacity số request tối đa trong một đợt dồn
     * @param perMinute số token nạp lại mỗi phút
     */
    public Decision check(String key, int capacity, int perMinute) {
        double refillPerSecond = perMinute / 60.0;
        try {
            @SuppressWarnings("unchecked")
            List<Long> result = redis.execute(script, List.of(key),
                    String.valueOf(capacity),
                    String.valueOf(refillPerSecond),
                    String.valueOf(clock.millis()),
                    "1");

            if (result == null || result.size() < 3) {
                return Decision.allowed(capacity, capacity);
            }
            boolean allowed = result.get(0) == 1L;
            int remaining = result.get(1).intValue();
            long retryAfterMillis = result.get(2);
            return new Decision(allowed, capacity, remaining, retryAfterMillis);

        } catch (DataAccessException e) {
            // Fail-open có chủ đích: mất chống bot còn hơn chặn nhầm người dùng thật
            log.warn("Redis không khả dụng, rate limit tạm thời bị bỏ qua (fail-open): {}", e.getMessage());
            return Decision.allowed(capacity, capacity);
        }
    }

    public record Decision(boolean allowed, int limit, int remaining, long retryAfterMillis) {

        static Decision allowed(int limit, int remaining) {
            return new Decision(true, limit, remaining, 0);
        }

        public long retryAfterSeconds() {
            return Math.max(1, (retryAfterMillis + 999) / 1000);
        }
    }
}
