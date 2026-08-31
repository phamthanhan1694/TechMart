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
 * Phien ban ban duoc cua san pham - don vi thuc su cua gia va ton kho.
 *
 * <p>Hai truong ton kho:
 * <ul>
 *   <li>{@code stockQuantity} - so hang thuc su con trong kho</li>
 *   <li>{@code reservedQuantity} - bo dem phi chuan hoa cua so hang dang bi giu</li>
 * </ul>
 * {@code available = stock - reserved} la con so hien thi cho khach. Giu bo dem o day
 * thay vi SUM bang inventory_reservations moi lan xem san pham; doi lai phai cap nhat
 * hai cho trong cung mot transaction va co job doi soat (docs/01-domain-v2.md muc 5.1).
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

    /** Tien luon BigDecimal, cot NUMERIC(15,2). Khong bao gio double. */
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

    /** So hang khach thuc su mua duoc ngay bay gio. */
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

    // --- Thao tac ton kho ---
    // Tat ca deu kiem tra bat bien tai cho. Day la lop bao ve thu hai;
    // lop thu nhat la khoa o tang service, lop thu ba la CHECK constraint o DB.

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

    /** Chot don: hang da giu chuyen thanh hang da ban. */
    public void commitHold(int quantity) {
        if (reservedQuantity < quantity || stockQuantity < quantity) {
            throw new IllegalStateException("Trang thai ton kho khong nhat quan khi chot don variant " + id);
        }
        this.reservedQuantity -= quantity;
        this.stockQuantity -= quantity;
    }

    /** Tru thang khong qua buoc giu - dung cho flash sale va cac luong mua ngay. */
    public void deduct(int quantity) {
        if (availableQuantity() < quantity) {
            throw new IllegalStateException(
                    "Khong du ton kho: con " + availableQuantity() + ", can " + quantity);
        }
        this.stockQuantity -= quantity;
    }

    /** Hoan kho khi huy don. Phai duoc goi dung mot lan cho moi don (idempotent o tang tren). */
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
