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
 * Bo dem ton kho flash sale tren Redis.
 *
 * <p>Trong luc phien dang chay, <b>Redis la nguon su that</b> - nguoc voi reservation
 * (noi DB la nguon su that). Ly do khac nhau: reservation can ben vung va khong bao gio
 * duoc mat; flash sale can chiu duoc vai nghin request/giay tren cung mot dong, va chap nhan
 * nhat quan cuoi voi DB (dong bo qua outbox, doi soat cuoi phien).
 *
 * <p>Khi Redis chet, flash sale <b>fail-closed</b>: tra 503, tu choi ban. Nguoc voi rate limit
 * fail-open. Cung mot ha tang chet, hai ung xu nguoc nhau - vi hau qua cua viec sai khac nhau:
 * chan nham nguoi dung that thi phien toai, con ban qua so suat thi phai den bu that.
 */
@Component
public class FlashSaleStockCounter {

    private static final Logger log = LoggerFactory.getLogger(FlashSaleStockCounter.class);
    private static final Duration USER_COUNTER_TTL = Duration.ofHours(24);

    /** Ket qua Lua tra ve, xem redis/flashsale-purchase.lua */
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
     * Nap san ton kho vao Redis TRUOC gio mo ban.
     *
     * <p>Neu de lan request dau tien tu nap thi dung giay mo ban se co hang nghin request
     * cung nap mot luc - <b>cache stampede</b>. Nap truoc la cach chan re nhat.
     */
    public void warmUp(FlashSaleItem item) {
        redis.opsForValue().set(stockKey(item.getId()), String.valueOf(item.remaining()));
        log.info("Da nap san ton kho flash sale item {}: {} suat", item.getId(), item.remaining());
    }

    /** Tru ton nguyen tu. Tra ve so am la ma loi - xem cac hang so o tren. */
    public long tryPurchase(Long itemId, Long userId, int quantity, int perUserLimit) {
        Long result = redis.execute(purchaseScript,
                List.of(stockKey(itemId), userKey(itemId, userId)),
                String.valueOf(quantity),
                String.valueOf(perUserLimit),
                String.valueOf(USER_COUNTER_TTL.toSeconds()));
        return result != null ? result : NOT_WARMED_UP;
    }

    /** Tra lai suat khi buoc tao don phia sau that bai. */
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
