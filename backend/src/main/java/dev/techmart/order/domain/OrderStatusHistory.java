package dev.techmart.order.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Nhat ky đổi trạng thái: ai đổi, từ dau sang dau, lúc nao, vì sao.
 * Kho hàng và đơn hàng là nhưng thu phải truy vết được - mọi hệ thống thật deu có bằng nay.
 */
@Entity
@Table(name = "order_status_history")
public class OrderStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    private OrderStatus toStatus;

    /** null khi hành động đó hệ thống thực hien (webhook thanh toán, job tự động). */
    @Column(name = "changed_by")
    private Long changedBy;

    @Column(length = 500)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OrderStatusHistory() {
    }

    OrderStatusHistory(Order order, OrderStatus fromStatus, OrderStatus toStatus,
                       Long changedBy, String note, Instant createdAt) {
        this.order = order;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedBy = changedBy;
        this.note = note;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public OrderStatus getFromStatus() { return fromStatus; }
    public OrderStatus getToStatus() { return toStatus; }
    public Long getChangedBy() { return changedBy; }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }
}
