package dev.techmart.flashsale.domain;

import dev.techmart.catalog.domain.ProductVariant;
import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * Một suất bán trong phiên flash sale.
 *
 * <p>{@code soldQuantity} ở DB là bản ghi <b>cuối cùng</b>, được đồng bộ từ Redis qua outbox.
 * Trong lúc phiên đang chạy, nguồn sự thật là bộ đếm trên Redis - đây là
 * <i>nhất quán cuối (eventual consistency) có chủ đích</i>, đổi lấy throughput.
 * Kết thúc phiên có job đối soát: {@code soldQuantity} phải bằng
 * {@code saleQuantity - tồn còn lại trên Redis}.
 */
@Entity
@Table(name = "flash_sale_items")
public class FlashSaleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flash_sale_id", nullable = false)
    private FlashSale flashSale;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @Column(name = "sale_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal salePrice;

    @Column(name = "sale_quantity", nullable = false)
    private Integer saleQuantity;

    @Column(name = "sold_quantity", nullable = false)
    private Integer soldQuantity;

    @Column(name = "per_user_limit", nullable = false)
    private Integer perUserLimit;

    @Version
    @Column(nullable = false)
    private Long version;

    protected FlashSaleItem() {
    }

    public FlashSaleItem(ProductVariant variant, BigDecimal salePrice, int saleQuantity, int perUserLimit) {
        this.variant = variant;
        this.salePrice = salePrice;
        this.saleQuantity = saleQuantity;
        this.perUserLimit = perUserLimit;
        this.soldQuantity = 0;
    }

    public Long getId() { return id; }
    public FlashSale getFlashSale() { return flashSale; }
    public ProductVariant getVariant() { return variant; }
    public BigDecimal getSalePrice() { return salePrice; }
    public Integer getSaleQuantity() { return saleQuantity; }
    public Integer getSoldQuantity() { return soldQuantity; }
    public Integer getPerUserLimit() { return perUserLimit; }

    public int remaining() {
        return saleQuantity - soldQuantity;
    }

    void attachTo(FlashSale flashSale) {
        this.flashSale = flashSale;
    }

    /** Đồng bộ từ Redis về DB. CHECK constraint ở DB chặn việc vượt qua saleQuantity. */
    public void recordSold(int quantity) {
        this.soldQuantity += quantity;
    }
}
