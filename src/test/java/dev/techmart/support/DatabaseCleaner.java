package dev.techmart.support;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Don du lieu giua cac test.
 *
 * <p>Dung TRUNCATE thu cong thay vi {@code @Transactional} tren test class. Ly do quan trong:
 * {@code @Transactional} tren test lam moi thread trong concurrency test chay chung MOT
 * transaction - test se pass gia trong khi production van oversell.
 * Xem docs/05-test-plan.md muc 2.
 */
@Component
public class DatabaseCleaner {

    private static final List<String> TABLES = List.of(
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
     * Don ca Postgres va Redis.
     *
     * <p><b>Phai don ca Redis</b>: {@code TRUNCATE ... RESTART IDENTITY} lam id bat dau lai tu 1,
     * nen key Redis dang {@code fs:{itemId}:user:{userId}} cua test truoc se trung voi
     * cap id cua test sau. Trieu chung da gap: mot user hoan toan moi bi tu choi voi
     * FLASH_SALE_LIMIT_EXCEEDED vi bo dem cu con sot lai.
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
