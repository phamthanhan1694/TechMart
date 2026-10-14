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
 * Bo đếm tồn kho flash sale trên Redis.
 *
 * <p>Trong lúc phiên dạng chay, <b>Redis là nguon su thật</b> - nguoc với reservation
 * (noi DB là nguon su thật). Lý do khác nhau: reservation cần ben vung và không bao giờ
 * được mat; flash sale cần chịu được vài nghìn request/giây trên cùng một dòng, và chap nhận
 * nhat quan cuoi với DB (dòng bỏ qua outbox, đối soát cuoi phiên).
 *
 * <p>Khi Redis chết, flash sale <b>fail-closed</b>: trả 503, từ chối bán. Nguoc với rate limit
 * fail-open. Cũng một hạ tầng chet, hai ung xu nguoc nhau - vì hau qua của viec sai khác nhau:
 * chặn nhầm người dùng thật thì phiên toai, con bán quá so suat thì phải den bu thật.
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
     * Nạp san tồn kho vào Redis TRUOC giỏ mo ban.
     *
     * <p>Nếu để lan request dau tien từ nạp thì dùng giây mo ban sẽ có hàng nghìn request
     * cũng nạp một lúc - <b>cache stampede</b>. Nạp trước là cach chặn re nhat.
     */
    public void warmUp(FlashSaleItem item) {
        redis.opsForValue().set(stockKey(item.getId()), String.valueOf(item.remaining()));
        log.info("Da nap san ton kho flash sale item {}: {} suat", item.getId(), item.remaining());
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

    /** Trả lai sửat khi buoc tạo đơn phia sau thất bại. */
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
