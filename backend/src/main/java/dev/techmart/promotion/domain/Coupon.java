package dev.techmart.promotion.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Mã giảm giá.
 *
 * <p>{@code usedCount} + {@code @Version} tạo ra <b>bài toán đồng thời thứ hai</b> của hệ thống:
 * 50 người cùng dùng một mã còn 1 lượt thì đúng một người được. Dùng lại chính cơ chế khoá
 * đã viết cho tồn kho, nên gần như miễn phí về công sức mà vẫn là một câu chuyện kỹ thuật riêng.
 *
 * <p>Mọi phép tính tiền đều bằng {@code BigDecimal} và làm tròn {@code HALF_UP} về 2 chữ số.
 */
@Entity
@Table(name = "coupons")
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DiscountType type;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal value;

    @Column(name = "min_order_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal minOrderAmount;

    /** Trần giảm giá cho loại PERCENT. null = không giới hạn. */
    @Column(name = "max_discount", precision = 15, scale = 2)
    private BigDecimal maxDiscount;

    /** null = không giới hạn tổng số lượt. */
    @Column(name = "usage_limit")
    private Integer usageLimit;

    @Column(name = "used_count", nullable = false)
    private Integer usedCount;

    @Column(name = "per_user_limit", nullable = false)
    private Integer perUserLimit;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(nullable = false)
    private Boolean active;

    @Version
    @Column(nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Coupon() {
    }

    public Coupon(String code, DiscountType type, BigDecimal value, BigDecimal minOrderAmount,
                  BigDecimal maxDiscount, Integer usageLimit, Integer perUserLimit,
                  Instant startsAt, Instant endsAt) {
        this.code = code.toUpperCase();
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount != null ? minOrderAmount : BigDecimal.ZERO;
        this.maxDiscount = maxDiscount;
        this.usageLimit = usageLimit;
        this.perUserLimit = perUserLimit != null ? perUserLimit : 1;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.usedCount = 0;
        this.active = true;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public DiscountType getType() { return type; }
    public BigDecimal getValue() { return value; }
    public BigDecimal getMinOrderAmount() { return minOrderAmount; }
    public BigDecimal getMaxDiscount() { return maxDiscount; }
    public Integer getUsageLimit() { return usageLimit; }
    public Integer getUsedCount() { return usedCount; }
    public Integer getPerUserLimit() { return perUserLimit; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public Boolean getActive() { return active; }

    public boolean isWithinPeriod(Instant now) {
        return !now.isBefore(startsAt) && !now.isAfter(endsAt);
    }

    public boolean hasQuotaLeft() {
        return usageLimit == null || usedCount < usageLimit;
    }

    public boolean meetsMinimum(BigDecimal subtotal) {
        return subtotal.compareTo(minOrderAmount) >= 0;
    }

    /**
     * Số tiền được giảm cho một giá trị đơn hàng.
     *
     * <p>Không bao giờ vượt qua {@code subtotal}: giảm giá làm tổng đơn âm là vô nghĩa
     * và là chỗ để bị lợi dụng.
     */
    public BigDecimal discountFor(BigDecimal subtotal) {
        BigDecimal discount = switch (type) {
            case PERCENT -> subtotal.multiply(value)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            case FIXED -> value;
        };
        if (type == DiscountType.PERCENT && maxDiscount != null) {
            discount = discount.min(maxDiscount);
        }
        return discount.min(subtotal).setScale(2, RoundingMode.HALF_UP);
    }

    /** Gọi trong transaction đặt hàng, sau khi đã khoá dòng này. */
    public void consume() {
        if (!hasQuotaLeft()) {
            throw new IllegalStateException("Mã giảm giá đã hết lượt");
        }
        this.usedCount++;
    }

    /** Hoàn lượt khi huỷ đơn. */
    public void release() {
        if (this.usedCount > 0) {
            this.usedCount--;
        }
    }

    public void deactivate() {
        this.active = false;
    }
}
