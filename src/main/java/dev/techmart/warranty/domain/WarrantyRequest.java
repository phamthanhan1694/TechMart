package dev.techmart.warranty.domain;

import dev.techmart.identity.domain.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Yeu cau bao hanh cho mot don vi hang cu the (theo serial).
 *
 * <p>Gan voi {@link ProductSerial} chu khong phai voi san pham hay don hang: bao hanh
 * la chuyen cua <i>cai may nay</i>, khong phai cua <i>dong may nay</i>.
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
     * Bang chuyen trang thai, cung tinh than voi OrderStateMachine nhung don gian hon
     * nen de thang trong entity: REQUESTED -> APPROVED/REJECTED, APPROVED -> COMPLETED.
     */
    public void transitionTo(WarrantyStatus next, String resolution, Instant now) {
        boolean allowed = switch (status) {
            case REQUESTED -> next == WarrantyStatus.APPROVED || next == WarrantyStatus.REJECTED;
            case APPROVED -> next == WarrantyStatus.COMPLETED;
            case REJECTED, COMPLETED -> false;
        };
        if (!allowed) {
            throw new IllegalStateException("Khong the chuyen tu " + status + " sang " + next);
        }
        this.status = next;
        this.resolution = resolution;
        if (next.isClosed()) {
            this.resolvedAt = now;
        }
    }
}
