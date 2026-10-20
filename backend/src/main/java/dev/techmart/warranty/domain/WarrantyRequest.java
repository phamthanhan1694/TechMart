package dev.techmart.warranty.domain;

import dev.techmart.identity.domain.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Yêu cầu bảo hành cho một đơn vị hàng cụ thể (theo serial).
 *
 * <p>Gắn với {@link ProductSerial} chứ không phải với sản phẩm hay đơn hàng: bảo hành
 * là chuyện của <i>cái máy này</i>, không phải của <i>dòng máy này</i>.
 */
@Entity
@Table(name = "warranty_requests")
public class WarrantyRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "serial_id", nullable = false)
    private ProductSerial serial;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WarrantyStatus status;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(length = 1000)
    private String resolution;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected WarrantyRequest() {
    }

    public WarrantyRequest(ProductSerial serial, User user, String reason) {
        this.serial = serial;
        this.user = user;
        this.reason = reason;
        this.status = WarrantyStatus.REQUESTED;
    }

    public Long getId() { return id; }
    public ProductSerial getSerial() { return serial; }
    public User getUser() { return user; }
    public WarrantyStatus getStatus() { return status; }
    public String getReason() { return reason; }
    public String getResolution() { return resolution; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getResolvedAt() { return resolvedAt; }

    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }

    /**
     * Bảng chuyển trạng thái, cùng tinh thần với OrderStateMachine nhưng đơn giản hơn
     * nên để thẳng trong entity: REQUESTED -> APPROVED/REJECTED, APPROVED -> COMPLETED.
     */
    public void transitionTo(WarrantyStatus next, String resolution, Instant now) {
        boolean allowed = switch (status) {
            case REQUESTED -> next == WarrantyStatus.APPROVED || next == WarrantyStatus.REJECTED;
            case APPROVED -> next == WarrantyStatus.COMPLETED;
            case REJECTED, COMPLETED -> false;
        };
        if (!allowed) {
            throw new IllegalStateException("Không thể chuyển từ " + status + " sang " + next);
        }
        this.status = next;
        this.resolution = resolution;
        if (next.isClosed()) {
            this.resolvedAt = now;
        }
    }
}
