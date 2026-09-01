package dev.techmart.payment.domain;

import dev.techmart.order.domain.Order;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Giao dich thanh toan.
 *
 * <p>Hai rang buoc UNIQUE la phan quan trong nhat cua bang nay:
 * <ul>
 *   <li>{@code idempotency_key} - chan tao hai giao dich cho mot lan bam</li>
 *   <li>{@code transaction_ref} - chan webhook gui trung lam doi trang thai hai lan</li>
 * </ul>
 * Cong thanh toan that <b>se</b> gui lai callback khi khong nhan duoc 200, nen webhook
 * bat buoc phai idempotent.
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "transaction_ref", unique = true, length = 100)
    private String transactionRef;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "paid_at")
    private Instant paidAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Payment() {
    }

    public Payment(Order order, PaymentMethod method, BigDecimal amount,
                   String transactionRef, String idempotencyKey) {
        this.order = order;
        this.method = method;
        this.amount = amount;
        this.transactionRef = transactionRef;
        this.idempotencyKey = idempotencyKey;
        this.status = PaymentStatus.INITIATED;
    }

    public Long getId() { return id; }
    public Order getOrder() { return order; }
    public PaymentMethod getMethod() { return method; }
    public PaymentStatus getStatus() { return status; }
    public BigDecimal getAmount() { return amount; }
    public String getTransactionRef() { return transactionRef; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getFailureReason() { return failureReason; }
    public Instant getPaidAt() { return paidAt; }

    public boolean isSettled() {
        return status == PaymentStatus.SUCCEEDED || status == PaymentStatus.REFUNDED;
    }

    public void markSucceeded(Instant now) {
        this.status = PaymentStatus.SUCCEEDED;
        this.paidAt = now;
        this.failureReason = null;
    }

    public void markFailed(String reason) {
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
    }

    public void markRefunded() {
        this.status = PaymentStatus.REFUNDED;
    }
}
