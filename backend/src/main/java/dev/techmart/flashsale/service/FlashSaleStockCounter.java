package dev.techmart.flashsale.service;

import dev.techmart.flashsale.domain.FlashSaleItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Bộ đếm tồn kho flash sale trên Redis.
 *
 * <p>Trong lúc phiên đang chạy, <b>Redis là nguồn sự thật</b> - ngược với reservation
 * (nơi DB là nguồn sự thật). Lý do khác nhau: reservation cần bền vững và không bao giờ
 * được mất; flash sale cần chịu được vài nghìn request/giây trên cùng một dòng, và chấp nhận
 * nhất quán cuối với DB (đồng bộ qua outbox, đối soát cuối phiên).
 *
 * <p>Khi Redis chết, flash sale <b>fail-closed</b>: trả 503, từ chối bán. Ngược với rate limit
 * fail-open. Cũng một hạ tầng chết, hai ứng xử ngược nhau - vì hậu quả của việc sai khác nhau:
 * chặn nhầm người dùng thật thì phiền toái, còn bán quá số suất thì phải đền bù thật.
 */
@Component
public class FlashSaleStockCounter {

    private static final Logger log = LoggerFactory.getLogger(FlashSaleStockCounter.class);
    private static final Duration USER_COUNTER_TTL = Duration.ofHours(24);

    /** Kết quả Lua trả về, xem redis/flashsale-purchase.lua */
    public static final long SOLD_OUT = -1L;
    public static final long USER_LIMIT_EXCEEDED = -2L;
    public static final long NOT_WARMED_UP = -3L;

    private final StringRedisTemplate redis;
    private final RedisScript<Long> purchaseScript;

    public FlashSaleStockCounter(StringRedisTemplate redis, RedisScript<Long> flashSalePurchaseScript) {
        this.redis = redis;
        this.purchaseScript = flashSalePurchaseScript;
    }

    /**
     * Nạp sẵn tồn kho vào Redis TRƯỚC giờ mở bán.
     *
     * <p>Nếu để lần request đầu tiên tự nạp thì đúng giây mở bán sẽ có hàng nghìn request
     * cùng nạp một lúc - <b>cache stampede</b>. Nạp trước là cách chặn rẻ nhất.
     */
    public void warmUp(FlashSaleItem item) {
        redis.opsForValue().set(stockKey(item.getId()), String.valueOf(item.remaining()));
        log.info("Đã nạp sẵn tồn kho flash sale item {}: {} suất", item.getId(), item.remaining());
    }

    /** Trừ tồn nguyên tử. Trả về số âm là mã lỗi - xem các hằng số ở trên. */
    public long tryPurchase(Long itemId, Long userId, int quantity, int perUserLimit) {
        Long result = redis.execute(purchaseScript,
                List.of(stockKey(itemId), userKey(itemId, userId)),
                String.valueOf(quantity),
                String.valueOf(perUserLimit),
                String.valueOf(USER_COUNTER_TTL.toSeconds()));
        return result != null ? result : NOT_WARMED_UP;
    }

    /** Trả lại suất khi bước tạo đơn phía sau thất bại. */
    public void refund(Long itemId, Long userId, int quantity) {
        redis.opsForValue().increment(stockKey(itemId), quantity);
        redis.opsForValue().decrement(userKey(itemId, userId), quantity);
    }

    public Optional<Integer> remaining(Long itemId) {
        String value = redis.opsForValue().get(stockKey(itemId));
        return value == null ? Optional.empty() : Optional.of(Integer.parseInt(value));
    }

    public void clear(Long itemId) {
        redis.delete(stockKey(itemId));
    }

    private static String stockKey(Long itemId) {
        return "fs:" + itemId + ":stock";
    }

    private static String userKey(Long itemId, Long userId) {
        return "fs:" + itemId + ":user:" + userId;
    }
}
