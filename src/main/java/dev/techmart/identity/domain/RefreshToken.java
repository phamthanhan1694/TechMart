package dev.techmart.identity.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Refresh token co xoay vong (rotation).
 *
 * <p>Luu HASH chu khong luu token thô: DB ro ri thi token van khong dung duoc.
 * {@code replacedBy} tao thanh chuoi token - dung de phat hien tai su dung:
 * mot token da bi thu hoi ma van duoc gui len nghia la co hai ben dang giu no,
 * khi do thu hoi ca chuoi (docs/07-security-checklist.md muc 1).
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by")
    private Long replacedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RefreshToken() {
        // JPA
    }

    public RefreshToken(User user, String tokenHash, Instant expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public Long getReplacedBy() { return replacedBy; }

    public boolean isRevoked() { return revokedAt != null; }

    public boolean isExpired(Instant now) { return now.isAfter(expiresAt); }

    public boolean isUsable(Instant now) { return !isRevoked() && !isExpired(now); }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            this.revokedAt = now;
        }
    }

    public void replaceWith(RefreshToken next, Instant now) {
        revoke(now);
        this.replacedBy = next.getId();
    }
}
