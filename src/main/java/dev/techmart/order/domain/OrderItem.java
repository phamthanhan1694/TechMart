package dev.techmart.order.domain;

import dev.techmart.catalog.domain.ProductVariant;
import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * Mot dong trong don hang.
 *
 * <p><b>Snapshot day du</b>: ten san pham, ten phien ban, SKU va don gia deu duoc chep lai
 * tai thoi diem dat hang. Khong tham chieu sang {@code products} de lay ten hay gia:
 * san pham co the doi ten, doi gia, hoac bi xoa mem sau nay, nhung hoa don cu phai in duoc
 * y nguyen. Day la nguyen tac "don hang la ban ghi lich su bat bien".
 *
 * <p>{@code variant} van duoc giu de hoan kho khi huy don va de thong ke ban hang.
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
        this.variant = variant;
        this.quantity = quantity;
        // Chep lai tai day - khong bao gio doc nguoc tu variant khi hien thi don cu
        this.productName = variant.getProduct().getName();
        this.variantName = variant.getName();
        this.sku = variant.getSku();
        this.unitPrice = variant.getPrice();
        this.lineTotal = variant.getPrice().multiply(BigDecimal.valueOf(quantity));
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
