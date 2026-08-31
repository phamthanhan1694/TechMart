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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void clean() {
        em.createNativeQuery("TRUNCATE TABLE " + String.join(", ", TABLES) + " RESTART IDENTITY CASCADE")
                .executeUpdate();
    }
}
