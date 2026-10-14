package dev.techmart.catalog.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Phiên ban bán được của sản phẩm - đơn vị thực sự của giá và tồn kho.
 *
 * <p>Hai truong tồn kho:
 * <ul>
 *   <li>{@code stockQuantity} - so hàng thực sự con trong kho</li>
 *   <li>{@code reservedQuantity} - bộ đếm phi chuẩn hoá của so hàng đang bị giữ</li>
 * </ul>
 * {@code available = stock - reserved} là con so hiển thị cho khách. Giữ bộ đếm ở đây
 * thay vì SUM bằng inventory_reservations mỗi lần xem sản phẩm; đổi lại phải cập nhật
 * hai chỗ trong cùng một transaction và có job đối soát (docs/01-domain-v2.md mục 5.1).
 *
 * <p>{@code version} phuc vu optimistic lock.
 */
@Entity
@Table(name = "product_variants")
@SQLRestriction("deleted_at IS NULL")
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, unique = true, length = 64)
    private String sku;

    @Column(nullable = false)
    private String name;

    /** Tien luon BigDecimal, cot NUMERIC(15,2). Không bao giờ double. */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal price;

    @Column(name = "compare_at_price", precision = 15, scale = 2)
    private BigDecimal compareAtPrice;

    @Column(name = "stock_quantity", nullable = false)
    private Integer stockQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private Integer reservedQuantity;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> attributes = new LinkedHashMap<>();

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProductVariant() {
    }

    public ProductVariant(String sku, String name, BigDecimal price, BigDecimal compareAtPrice,
                          int stockQuantity, Map<String, Object> attributes) {
        this.sku = sku;
        this.name = name;
        this.price = price;
        this.compareAtPrice = compareAtPrice;
        this.stockQuantity = stockQuantity;
        this.reservedQuantity = 0;
        this.attributes = attributes != null ? new LinkedHashMap<>(attributes) : new LinkedHashMap<>();
    }

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getCompareAtPrice() { return compareAtPrice; }
    public Integer getStockQuantity() { return stockQuantity; }
    public Integer getReservedQuantity() { return reservedQuantity; }
    public Map<String, Object> getAttributes() { return attributes; }
    public Long getVersion() { return version; }
    public Instant getDeletedAt() { return deletedAt; }

    /** So hàng khách thực sự mua được ngay bay giỏ. */
    public int availableQuantity() {
        return stockQuantity - reservedQuantity;
    }

    void attachTo(Product product) {
        this.product = product;
    }

    public void update(String name, BigDecimal price, BigDecimal compareAtPrice, Map<String, Object> attributes) {
        this.name = name;
        this.price = price;
        this.compareAtPrice = compareAtPrice;
        if (attributes != null) {
            this.attributes = new LinkedHashMap<>(attributes);
        }
    }

    public void softDelete(Instant now) {
        this.deletedAt = now;
    }

    // --- Thao tac tồn kho ---
    // Tất cả đều kiểm tra bất biến tại chỗ. Đây là lớp bảo vệ thứ hai;
    // lop thu nhat là khoá o tầng service, lop thu ba là CHECK constraint o DB.

    public void hold(int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("So luong giu phai >= 1");
        }
        if (availableQuantity() < quantity) {
            throw new IllegalStateException(
                    "Khong du hang de giu: con " + availableQuantity() + ", can " + quantity);
        }
        this.reservedQuantity += quantity;
    }

    public void releaseHold(int quantity) {
        this.reservedQuantity = Math.max(0, this.reservedQuantity - quantity);
    }

    /** Chot đơn: hàng đã giữ chuyen thanh hàng đã bán. */
    public void commitHold(int quantity) {
        if (reservedQuantity < quantity || stockQuantity < quantity) {
            throw new IllegalStateException("Trang thai ton kho khong nhat quan khi chot don variant " + id);
        }
        this.reservedQuantity -= quantity;
        this.stockQuantity -= quantity;
    }

    /** Trừ thẳng không qua bước giữ - đúng chỗ flash sale và các luồng mua ngay. */
    public void deduct(int quantity) {
        if (availableQuantity() < quantity) {
            throw new IllegalStateException(
                    "Khong du ton kho: con " + availableQuantity() + ", can " + quantity);
        }
        this.stockQuantity -= quantity;
    }

    /** Hoan kho khi huỷ đơn. Phải được gọi đúng một lần cho mỗi đơn (idempotent o tầng trên). */
    public void restock(int quantity) {
        this.stockQuantity += quantity;
    }

    public void adjustStock(int delta) {
        int next = this.stockQuantity + delta;
        if (next < reservedQuantity) {
            throw new IllegalStateException(
                    "Khong the ha ton kho xuong duoi so dang giu (" + reservedQuantity + ")");
        }
        this.stockQuantity = next;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProductVariant other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass());
    }
}
