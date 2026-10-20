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
 * Phiên bản bán được của sản phẩm - đơn vị thực sự của giá và tồn kho.
 *
 * <p>Hai trường tồn kho:
 * <ul>
 *   <li>{@code stockQuantity} - số hàng thực sự còn trong kho</li>
 *   <li>{@code reservedQuantity} - bộ đếm phi chuẩn hoá của số hàng đang bị giữ</li>
 * </ul>
 * {@code available = stock - reserved} là con số hiển thị cho khách. Giữ bộ đếm ở đây
 * thay vì SUM bảng inventory_reservations mỗi lần xem sản phẩm; đổi lại phải cập nhật
 * hai chỗ trong cùng một transaction và có job đối soát (docs/01-domain-v2.md mục 5.1).
 *
 * <p>{@code version} phục vụ optimistic lock.
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

    /** Tiền luôn BigDecimal, cột NUMERIC(15,2). Không bao giờ double. */
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

    /** Số hàng khách thực sự mua được ngay bây giờ. */
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

    // --- Thao tác tồn kho ---
    // Tất cả đều kiểm tra bất biến tại chỗ. Đây là lớp bảo vệ thứ hai;
    // lớp thứ nhất là khoá ở tầng service, lớp thứ ba là CHECK constraint ở DB.

    public void hold(int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("Số lượng giữ phải >= 1");
        }
        if (availableQuantity() < quantity) {
            throw new IllegalStateException(
                    "Không đủ hàng để giữ: còn " + availableQuantity() + ", cần " + quantity);
        }
        this.reservedQuantity += quantity;
    }

    public void releaseHold(int quantity) {
        this.reservedQuantity = Math.max(0, this.reservedQuantity - quantity);
    }

    /** Chốt đơn: hàng đã giữ chuyển thành hàng đã bán. */
    public void commitHold(int quantity) {
        if (reservedQuantity < quantity || stockQuantity < quantity) {
            throw new IllegalStateException("Trạng thái tồn kho không nhất quán khi chốt đơn variant " + id);
        }
        this.reservedQuantity -= quantity;
        this.stockQuantity -= quantity;
    }

    /** Trừ thẳng không qua bước giữ - đúng chỗ flash sale và các luồng mua ngay. */
    public void deduct(int quantity) {
        if (availableQuantity() < quantity) {
            throw new IllegalStateException(
                    "Không đủ tồn kho: còn " + availableQuantity() + ", cần " + quantity);
        }
        this.stockQuantity -= quantity;
    }

    /** Hoàn kho khi huỷ đơn. Phải được gọi đúng một lần cho mỗi đơn (idempotent ở tầng trên). */
    public void restock(int quantity) {
        this.stockQuantity += quantity;
    }

    public void adjustStock(int delta) {
        int next = this.stockQuantity + delta;
        if (next < reservedQuantity) {
            throw new IllegalStateException(
                    "Không thể ha tồn kho xuống dưới số đang giữ (" + reservedQuantity + ")");
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
