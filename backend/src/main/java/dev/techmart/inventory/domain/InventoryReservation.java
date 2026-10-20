package dev.techmart.inventory.domain;

import dev.techmart.catalog.domain.ProductVariant;
import dev.techmart.identity.domain.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Một lần giữ hàng.
 *
 * <p>Bảng này là <b>nguồn sự thật</b> của việc giữ hàng; Redis chỉ là đường nhanh.
 * Lý do chọn như vậy: Redis keyspace notification là at-most-once - mất một event là
 * hàng bị giữ vĩnh viễn; Redis restart là mất sạch reservation. Job quét DB mỗi 30 giây
 * chậm hơn nhưng không bao giờ mất. Xem ADR-0004.
 *
 * <p>{@code orderId} lưu dạng Long chứ không phải quan hệ JPA: reservation được tạo
 * Trước khi có đơn hàng, và module inventory không nên phụ thuộc ngược vào module order.
 */
@Entity
@Table(name = "inventory_reservations")
public class InventoryReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "order_id")
    private Long orderId;

    /** Gom các reservation của cùng một lần checkout lại với nhau. */
    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected InventoryReservation() {
    }

    public InventoryReservation(ProductVariant variant, User user, String sessionId,
                                int quantity, Instant expiresAt) {
        this.variant = variant;
        this.user = user;
        this.sessionId = sessionId;
        this.quantity = quantity;
        this.expiresAt = expiresAt;
        this.status = ReservationStatus.HELD;
    }

    public Long getId() { return id; }
    public ProductVariant getVariant() { return variant; }
    public User getUser() { return user; }
    public Long getOrderId() { return orderId; }
    public String getSessionId() { return sessionId; }
    public Integer getQuantity() { return quantity; }
    public ReservationStatus getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }

    public boolean isHeld() {
        return status == ReservationStatus.HELD;
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    public void commit(Long orderId) {
        this.status = ReservationStatus.COMMITTED;
        this.orderId = orderId;
    }

    public void release() {
        this.status = ReservationStatus.RELEASED;
    }

    public void expire() {
        this.status = ReservationStatus.EXPIRED;
    }
}
