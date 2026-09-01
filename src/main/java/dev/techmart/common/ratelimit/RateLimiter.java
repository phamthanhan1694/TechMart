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
 * Token bucket dung chung tren Redis.
 *
 * <p><b>Vi sao khong dem trong bo nho JVM:</b> bien dem do chi song trong mot instance.
 * Deploy hai instance la gioi han bi nhan doi ngay. Trang thai rate limit phai nam o tang
 * dung chung - cung mot ly do khien {@code synchronized} vo dung khi chong oversell.
 *
 * <p><b>Fail-open khi Redis chet.</b> Chan nham toan bo nguoi dung that te hon nhieu so voi
 * tam thoi mat kha nang chong bot. Nguoc voi flash sale (fail-closed) - xem
 * {@code docs/04-error-catalog.md} muc 9.
 *
 * <p>Viet bang Lua thay vi dung thu vien (Bucket4j) vi flash sale da can dung co che
 * Redis + Lua nay roi: mot khai niem cho ca hai tinh nang, va khong them phu thuoc.
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
     * @param key      dinh danh gau, vd {@code rl:login:203.0.113.7}
     * @param capacity so request toi da trong mot dot dồn
     * @param perMinute so token nap lai moi phut
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
            // Fail-open co chu dich: mat chong bot con hon chan nham nguoi dung that
            log.warn("Redis khong kha dung, rate limit tam thoi bi bo qua (fail-open): {}", e.getMessage());
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
