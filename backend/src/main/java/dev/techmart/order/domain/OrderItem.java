package dev.techmart.order.domain;

import dev.techmart.catalog.domain.ProductVariant;
import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * Một dòng trong đơn hàng.
 *
 * <p><b>Snapshot đầy đủ</b>: tên sản phẩm, tên phiên bản, SKU và đơn giá đều được chép lại
 * tại thời điểm đặt hàng. Không tham chiếu sang {@code products} để lấy tên hay giá:
 * sản phẩm có thể đổi tên, đổi giá, hoặc bị xoá mềm sau này, nhưng hoá đơn cũ phải in được
 * y nguyen. Đây là nguyen tac "đơn hàng là bản ghi lịch sử bất biến".
 *
 * <p>{@code variant} vẫn được giữ để hoàn kho khi huỷ đơn và để thống kê bán hàng.
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
     * Vẫn là snapshot: đơn cũ giữ nguyen giả đã bán, không đọc ngược từ variant.
     */
    public OrderItem(ProductVariant variant, int quantity, BigDecimal unitPrice) {
        this.variant = variant;
        this.quantity = quantity;
        // Chép lại tại đây - không bao giờ đọc ngược từ variant khi hiển thị đơn cũ
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
