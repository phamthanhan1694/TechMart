package dev.techmart.catalog.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Sản phẩm (dòng máy). Tồn kho KHÔNG nằm ở đây mà nằm ở {@link ProductVariant} -
 * iPhone 15 bản 128GB và 256GB có giá và tồn kho khác nhau.
 *
 * <p>{@code spec} là thông số chung của cả dòng máy, lưu JSONB có GIN index để lọc
 * ({@code spec @> '{"ram":"16GB"}'}). Xem docs/01-domain-v2.md mục 5.2 về JSONB vs EAV.
 *
 * <p>{@code @SQLRestriction} tự động thêm {@code deleted_at IS NULL} vào mọi truy vấn JPA -
 * để không bao giờ quên lọc sản phẩm đã xoá mềm. Truy vấn native phải tự thêm điều kiện này.
 */
@Entity
@Table(name = "products")
@SQLRestriction("deleted_at IS NULL")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "brand_id", nullable = false)
    private Brand brand;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> spec = new LinkedHashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<ProductVariant> variants = new ArrayList<>();

    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<ProductImage> images = new ArrayList<>();

    protected Product() {
    }

    public Product(Category category, Brand brand, String name, String slug,
                   String description, String thumbnailUrl, Map<String, Object> spec) {
        this.category = category;
        this.brand = brand;
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.thumbnailUrl = thumbnailUrl;
        this.spec = spec != null ? new LinkedHashMap<>(spec) : new LinkedHashMap<>();
        this.status = ProductStatus.ACTIVE;
    }

    public Long getId() { return id; }
    public Category getCategory() { return category; }
    public Brand getBrand() { return brand; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getDescription() { return description; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public Map<String, Object> getSpec() { return spec; }
    public ProductStatus getStatus() { return status; }
    public Instant getDeletedAt() { return deletedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public List<ProductVariant> getVariants() { return variants; }
    public List<ProductImage> getImages() { return images; }

    public void update(Category category, Brand brand, String name, String slug,
                       String description, String thumbnailUrl, Map<String, Object> spec, ProductStatus status) {
        this.category = category;
        this.brand = brand;
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.thumbnailUrl = thumbnailUrl;
        if (spec != null) {
            this.spec = new LinkedHashMap<>(spec);
        }
        if (status != null) {
            this.status = status;
        }
    }

    /**
     * Xoá mềm. Xoá cứng sẽ vi phạm khoá ngoại với order_items của đơn hàng cũ,
     * hoặc phá lịch sử đơn hàng - xem docs/01-domain-v2.md mục 1.
     */
    public void softDelete(Instant now) {
        this.deletedAt = now;
        this.variants.forEach(v -> v.softDelete(now));
    }

    public void addVariant(ProductVariant variant) {
        variants.add(variant);
        variant.attachTo(this);
    }

    public void addImage(ProductImage image) {
        images.add(image);
        image.attachTo(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Product other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass());
    }
}
