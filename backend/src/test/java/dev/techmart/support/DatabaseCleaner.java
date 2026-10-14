package dev.techmart.support;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Dọn dữ liệu giữa các test.
 *
 * <p>Dùng TRUNCATE thủ công thay vì {@code @Transactional} trên test class. Lý đó quan trọng:
 * {@code @Transactional} trên test làm mọi thread trong concurrency test chạy chung MỘT
 * transaction - test sẽ pass giả trong khi production vẫn oversell.
 * Xem docs/05-test-plan.md mục 2.
 */
@Component
public class DatabaseCleaner {

    private static final List<String> TABLES = List.of(
            "notifications", "audit_logs", "wishlist_items", "user_addresses",
            "order_status_history", "coupon_redemptions", "payments", "reviews",
            "warranty_requests", "product_serials", "order_items", "orders",
            "inventory_reservations", "cart_items", "carts",
            "flash_sale_items", "flash_sales", "coupons",
            "outbox_events", "idempotency_records",
            "product_images", "product_variants", "products",
            "categories", "brands", "refresh_tokens", "users");

    @PersistenceContext
    private EntityManager em;

    private final org.springframework.data.redis.core.StringRedisTemplate redis;

    public DatabaseCleaner(org.springframework.data.redis.core.StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Dọn cả Postgres và Redis.
     *
     * <p><b>Phải đơn ca Redis</b>: {@code TRUNCATE ... RESTART IDENTITY} làm id bắt đầu lại từ 1,
     * nên key Redis dạng {@code fs:{itemId}:user:{userId}} của test trước sẽ trùng với
     * cặp id của test sau. Triệu chứng đã gặp: một user hoàn toàn mới bị từ chối với
     * FLASH_SALE_LIMIT_EXCEEDED vì bộ đếm cũ còn sót lại.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void clean() {
        em.createNativeQuery("TRUNCATE TABLE " + String.join(", ", TABLES) + " RESTART IDENTITY CASCADE")
                .executeUpdate();
        flushRedis();
    }

    private void flushRedis() {
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }
}
