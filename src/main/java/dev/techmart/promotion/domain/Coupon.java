package dev.techmart.promotion.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Ma giam gia.
 *
 * <p>{@code usedCount} + {@code @Version} tao ra <b>bai toan dong thoi thu hai</b> cua he thong:
 * 50 nguoi cung dung mot ma con 1 luot thi dung mot nguoi duoc. Dung lai chinh co che khoa
 * da viet cho ton kho, nen gan nhu mien phi ve cong suc ma van la mot cau chuyen ky thuat rieng.
 *
 * <p>Moi phep tinh tien deu bang {@code BigDecimal} va lam tron {@code HALF_UP} ve 2 chu so.
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

    /** Tran giam gia cho loai PERCENT. null = khong gioi han. */
    @Column(name = "max_discount", precision = 15, scale = 2)
    private BigDecimal maxDiscount;

    /** null = khong gioi han tong so luot. */
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
     * So tien duoc giam cho mot gia tri don hang.
     *
     * <p>Khong bao gio vuot qua {@code subtotal}: giam gia lam tong don am la vo nghia
     * va la cho de bi loi dung.
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

    /** Goi trong transaction dat hang, sau khi da khoa dong nay. */
    public void consume() {
        if (!hasQuotaLeft()) {
            throw new IllegalStateException("Ma giam gia da het luot");
        }
        this.usedCount++;
    }

    /** Hoan luot khi huy don. */
    public void release() {
        if (this.usedCount > 0) {
            this.usedCount--;
        }
    }

    public void deactivate() {
        this.active = false;
    }
}
