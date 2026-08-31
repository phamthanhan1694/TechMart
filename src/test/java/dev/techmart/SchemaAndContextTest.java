package dev.techmart;

import dev.techmart.support.IntegrationTestBase;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test dau tien va quan trong nhat cua suite: ung dung khoi dong duoc voi PostgreSQL that.
 *
 * <p>Vi {@code ddl-auto: validate}, test nay that bai ngay khi mot entity lech voi schema
 * Flyway - sai ten cot, sai kieu, thieu cot. No bat duoc ca lop loi ma unit test khong thay.
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
                "warranty_requests", "outbox_events", "idempotency_records");
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
                "idx_products_spec_gin",       // loc theo thong so ky thuat
                "idx_products_name_trgm",      // tim theo ten
                "idx_reservations_expiry",     // job quet reservation qua han
                "idx_outbox_pending",          // worker outbox
                "idx_orders_user");            // danh sach don cua user
    }
}
