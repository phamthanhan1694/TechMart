package dev.techmart.order.domain;

import dev.techmart.catalog.domain.ProductVariant;
import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * Một dòng trong đơn hàng.
 *
 * <p><b>Snapshot đây du</b>: ten sản phẩm, ten phiên ban, SKU và đơn giá deu được chep lai
 * tai thời điểm đặt hàng. Không tham chieu sang {@code products} để lấy ten hay giá:
 * sản phẩm có thể đổi ten, đổi giá, hoac bi xoá mềm sau nay, nhưng hoá đơn cũ phải in được
 * y nguyen. Đây là nguyen tac "đơn hàng là bản ghi lich su bất biến".
 *
 * <p>{@code variant} van được giữ để hoàn kho khi huỷ đơn và để thong ke ban hàng.
 */
@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "variant_name", nullable = false, length = 150)
    private String variantName;

    @Column(nullable = false, length = 64)
    private String sku;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "line_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal lineTotal;

    protected OrderItem() {
    }

    public OrderItem(ProductVariant variant, int quantity) {
        this(variant, quantity, variant.getPrice());
    }

    /**
     * Dùng khi giá bán khác giả niem yet của variant - vd giá flash sale.
     * Vẫn là snapshot: đơn cũ giữ nguyen giả đã bán, không doc nguoc từ variant.
     */
    public OrderItem(ProductVariant variant, int quantity, BigDecimal unitPrice) {
        this.variant = variant;
        this.quantity = quantity;
        // Chep lai tại đây - không bao giờ doc nguoc từ variant khi hiển thị đơn cũ
        this.productName = variant.getProduct().getName();
        this.variantName = variant.getName();
        this.sku = variant.getSku();
        this.unitPrice = unitPrice;
        this.lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public Long getId() { return id; }
    public Order getOrder() { return order; }
    public ProductVariant getVariant() { return variant; }
    public String getProductName() { return productName; }
    public String getVariantName() { return variantName; }
    public String getSku() { return sku; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public Integer getQuantity() { return quantity; }
    public BigDecimal getLineTotal() { return lineTotal; }

    void attachTo(Order order) {
        this.order = order;
    }
}
