package dev.techmart.promotion.domain;

import dev.techmart.identity.domain.User;
import dev.techmart.order.domain.Order;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Mot lan su dung ma giam gia.
 *
 * <p>Rang buoc {@code UNIQUE(coupon_id, user_id, order_id)} o DB chan viec dung lai
 * cung mot ma tren cung mot don. Dem so dong theo (coupon, user) la cach kiem
 * {@code per_user_limit} - chinh xac hon la tin vao bo dem tren bang coupon.
 */
@Entity
@Table(name = "coupon_redemptions")
public class CouponRedemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coupon_id", nullable = false)
    private Coupon coupon;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CouponRedemption() {
    }

    public CouponRedemption(Coupon coupon, User user, Order order) {
        this.coupon = coupon;
        this.user = user;
        this.order = order;
    }

    public Long getId() { return id; }
    public Coupon getCoupon() { return coupon; }
    public Order getOrder() { return order; }
}
