package dev.techmart.warranty.domain;

import dev.techmart.catalog.domain.ProductVariant;
import dev.techmart.order.domain.OrderItem;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Một ĐƠN VỊ hàng cụ thể, định danh bằng serial/IMEI.
 *
 * <p><b>Vì sao có bảng này khi đã có {@code stock_quantity}:</b> hai cách quản lý tồn kho
 * trả lời hai câu hỏi khác nhau.
 * <ul>
 *   <li>Tồn kho <b>theo số lượng</b> ({@code stock_quantity}) trả lời "còn bao nhiêu cái"
 *       — đủ cho việc bán, và rẻ (một con số, một dòng cần khoá).</li>
 *   <li>Tồn kho <b>theo serial</b> trả lời "cái nào đang ở đâu" — bắt buộc khi cần bảo hành,
 *       thu hồi theo lô, hay truy vết đơn vị hàng cụ thể.</li>
 * </ul>
 *
 * <p>Hệ thống này dùng <b>cả hai</b>: bán và chống oversell dựa trên {@code stock_quantity}
 * (nhanh, khoá một dòng); serial chỉ được gán khi <b>đóng gói</b>. Nếu gán serial ngay lúc
 * đặt hàng thì mỗi lần mua phải khoá và cập nhật N dòng serial thay vì một dòng đếm —
 * đúng chỗ đang tranh chấp nặng nhất.
 */
@Entity
@Table(name = "product_serials")
public class ProductSerial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @Column(name = "serial_no", nullable = false, unique = true, length = 64)
    private String serialNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SerialStatus status;

    /** null khi còn trong kho. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id")
    private OrderItem orderItem;

    @Column(name = "warranty_months", nullable = false)
    private Integer warrantyMonths;

    /** Bảo hành tính từ lúc xuất kho, không phải từ lúc nhập kho. */
    @Column(name = "activated_at")
    private Instant activatedAt;

    protected ProductSerial() {
    }

    public ProductSerial(ProductVariant variant, String serialNo, int warrantyMonths) {
        this.variant = variant;
        this.serialNo = serialNo.toUpperCase();
        this.warrantyMonths = warrantyMonths;
        this.status = SerialStatus.IN_STOCK;
    }

    public Long getId() { return id; }
    public ProductVariant getVariant() { return variant; }
    public String getSerialNo() { return serialNo; }
    public SerialStatus getStatus() { return status; }
    public OrderItem getOrderItem() { return orderItem; }
    public Integer getWarrantyMonths() { return warrantyMonths; }
    public Instant getActivatedAt() { return activatedAt; }

    public Instant warrantyExpiresAt() {
        return activatedAt == null ? null : activatedAt.plus(warrantyMonths * 30L, ChronoUnit.DAYS);
    }

    public boolean isUnderWarranty(Instant now) {
        Instant expiry = warrantyExpiresAt();
        return expiry != null && now.isBefore(expiry);
    }

    /** Gán cho một dòng đơn hàng khi đóng gói. Bảo hành bắt đầu tính từ đây. */
    public void assignTo(OrderItem orderItem, Instant now) {
        if (status != SerialStatus.IN_STOCK) {
            throw new IllegalStateException(
                    "Serial " + serialNo + " dang o trang thai " + status + ", khong the xuat kho");
        }
        this.orderItem = orderItem;
        this.status = SerialStatus.SOLD;
        this.activatedAt = now;
    }

    /** Khách trả hàng — quay lại kho nhưng KHÔNG về IN_STOCK cho tới khi kiểm tra xong. */
    public void markReturned() {
        this.status = SerialStatus.RETURNED;
    }

    public void markDefective() {
        this.status = SerialStatus.DEFECTIVE;
    }

    /** Kiểm tra xong, hàng còn tốt, cho quay lại kho bán tiếp. */
    public void restock() {
        this.status = SerialStatus.IN_STOCK;
        this.orderItem = null;
        this.activatedAt = null;
    }
}
