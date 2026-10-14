package dev.techmart;

import dev.techmart.support.IntegrationTestBase;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test đầu tiên và quan trọng nhất của suite: ứng dụng khởi động được với PostgreSQL thật.
 *
 * <p>Vì {@code ddl-auto: validate}, test này thất bại ngay khi một entity lech với schema
 * Flyway - sai tên cột, sai kiểu, thiếu cột. Nó bắt được ca lớp lỗi mà unit test không thấy.
 */
class SchemaAndContextTest extends IntegrationTestBase {

    @Autowired
    EntityManager em;

    @Test
    @DisplayName("Context khoi dong duoc va schema Flyway khop voi toan bo entity")
    void contextLoadsAndSchemaMatchesEntities() {
        assertThat(em).isNotNull();
    }

    @Test
    @DisplayName("Migration V1 tao du toan bo bang cua schema v2")
    void allTablesExist() {
        @SuppressWarnings("unchecked")
        List<String> tables = em.createNativeQuery("""
                SELECT table_name FROM information_schema.tables
                 WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                """).getResultList();

        assertThat(tables).contains(
                "users", "refresh_tokens", "brands", "categories", "products",
                "product_variants", "product_images", "carts", "cart_items",
                "inventory_reservations", "coupons", "orders", "order_items",
                "order_status_history", "coupon_redemptions", "payments",
                "flash_sales", "flash_sale_items", "reviews", "product_serials",
                "warranty_requests", "outbox_events", "idempotency_records",
                // V2
                "user_addresses", "wishlist_items", "notifications", "audit_logs");
    }

    @Test
    @DisplayName("Chot chan cuoi cung o DB: khong the de ton kho am hay giu qua ton")
    void databaseEnforcesStockInvariants() {
        @SuppressWarnings("unchecked")
        List<String> constraints = em.createNativeQuery("""
                SELECT conname FROM pg_constraint
                 WHERE conrelid = 'product_variants'::regclass AND contype = 'c'
                """).getResultList();

        assertThat(constraints).contains(
                "variants_stock_nonneg",
                "variants_reserved_nonneg",
                "variants_reserved_lte_stock");
    }

    @Test
    @DisplayName("Cac index quan trong deu ton tai (GIN cho JSONB, trigram cho ten, partial cho job quet)")
    void criticalIndexesExist() {
        @SuppressWarnings("unchecked")
        List<String> indexes = em.createNativeQuery(
                "SELECT indexname FROM pg_indexes WHERE schemaname = 'public'").getResultList();

        assertThat(indexes).contains(
                "idx_products_spec_gin",       // lọc theo thông số kỹ thuật
                "idx_products_name_trgm",      // tìm theo tên
                "idx_reservations_expiry",     // job quét reservation quá hạn
                "idx_outbox_pending",          // worker outbox
                "idx_orders_user",            // danh sách đơn của user
                "idx_orders_placed_at",       // mọi truy vấn thống kê lọc theo ngày đặt
                "uq_addresses_one_default",   // tối đa một địa chỉ mặc định
                "idx_notifications_unread");  // endpoint đếm chưa đọc bị polling
    }
}
