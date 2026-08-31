package dev.techmart.order.domain;

import dev.techmart.identity.domain.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Don hang - ban ghi lich su bat bien.
 *
 * <p>{@code code} la dinh danh doi ngoai (TM-20260901-0001), khong phoi {@code id} tuan tu:
 * id tuan tu tiet lo so don moi ngay cua cua hang va tao co hoi do tuan tu.
 *
 * <p>{@code @Version} chan tinh huong khach bam huy dung luc admin bam chuyen sang PACKED:
 * hai transaction cung doc PAID, ca hai cung thay hop le. Nguoi thua nhan 409.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "discount_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "shipping_fee", nullable = false, precision = 15, scale = 2)
    private BigDecimal shippingFee;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "coupon_code", length = 50)
    private String couponCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "shipping_address", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> shippingAddress = new LinkedHashMap<>();

    @Column(length = 500)
    private String note;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "placed_at", nullable = false)
    private Instant placedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<OrderStatusHistory> history = new ArrayList<>();

    protected Order() {
    }

    public Order(String code, User user, Map<String, Object> shippingAddress,
                 String note, String idempotencyKey, Instant placedAt) {
        this.code = code;
        this.user = user;
        this.shippingAddress = new LinkedHashMap<>(shippingAddress);
        this.note = note;
        this.idempotencyKey = idempotencyKey;
        this.placedAt = placedAt;
        this.status = OrderStatus.PENDING_PAYMENT;
        this.subtotal = BigDecimal.ZERO;
        this.discountAmount = BigDecimal.ZERO;
        this.shippingFee = BigDecimal.ZERO;
        this.totalAmount = BigDecimal.ZERO;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public User getUser() { return user; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public BigDecimal getShippingFee() { return shippingFee; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getCouponCode() { return couponCode; }
    public Map<String, Object> getShippingAddress() { return shippingAddress; }
    public String getNote() { return note; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getPlacedAt() { return placedAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public Instant getCreatedAt() { return createdAt; }
    public List<OrderItem> getItems() { return items; }
    public List<OrderStatusHistory> getHistory() { return history; }

    public void addItem(OrderItem item) {
        items.add(item);
        item.attachTo(this);
    }

    /**
     * Chot tien. Tinh lai tu order_items o server, khong bao gio tin so tien client gui len.
     */
    public void recalculateTotals(BigDecimal shippingFee, BigDecimal discountAmount, String couponCode) {
        this.subtotal = items.stream()
                .map(OrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        this.shippingFee = shippingFee != null ? shippingFee : BigDecimal.ZERO;
        this.discountAmount = discountAmount != null ? discountAmount : BigDecimal.ZERO;
        this.couponCode = couponCode;

        BigDecimal total = this.subtotal.add(this.shippingFee).subtract(this.discountAmount);
        this.totalAmount = total.max(BigDecimal.ZERO);   // giam gia khong bao gio lam tong am
    }

    /** Chi OrderService duoc goi, va chi sau khi da kiem qua OrderStateMachine. */
    public void applyStatus(OrderStatus next, Instant now) {
        this.status = next;
        if (next == OrderStatus.DELIVERED) {
            this.deliveredAt = now;
        }
    }

    public void logTransition(OrderStatus from, OrderStatus to, Long changedBy, String note, Instant now) {
        history.add(new OrderStatusHistory(this, from, to, changedBy, note, now));
    }

    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Order other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass());
    }
}
