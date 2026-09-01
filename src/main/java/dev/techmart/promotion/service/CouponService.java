package dev.techmart.promotion.service;

import dev.techmart.common.error.BusinessException;
import dev.techmart.common.error.ErrorCode;
import dev.techmart.identity.repository.UserRepository;
import dev.techmart.order.domain.Order;
import dev.techmart.promotion.domain.Coupon;
import dev.techmart.promotion.domain.CouponRedemption;
import dev.techmart.promotion.dto.CouponDtos.*;
import dev.techmart.promotion.repository.CouponRedemptionRepository;
import dev.techmart.promotion.repository.CouponRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class CouponService {

    private static final Logger log = LoggerFactory.getLogger(CouponService.class);

    private final CouponRepository coupons;
    private final CouponRedemptionRepository redemptions;
    private final UserRepository users;
    private final Clock clock;

    public CouponService(CouponRepository coupons, CouponRedemptionRepository redemptions,
                         UserRepository users, Clock clock) {
        this.coupons = coupons;
        this.redemptions = redemptions;
        this.users = users;
        this.clock = clock;
    }

    /**
     * Kiem tra ma de hien thi truoc cho khach.
     *
     * <p><b>Khong giu cho.</b> Viec tru luot that su chi xay ra trong transaction dat hang,
     * co khoa dong. Khach thay "hop le" roi luc dat bi COUPON_USED_UP la hanh vi dung,
     * khong phai bug - dat cho theo ma giam gia se khoa luot cho nhung gio hang bi bo quen.
     */
    @Transactional(readOnly = true)
    public ValidateResponse validate(String code, BigDecimal subtotal, Long userId) {
        var found = coupons.findByCodeIgnoreCase(code);
        if (found.isEmpty()) {
            return ValidateResponse.invalid(ErrorCode.COUPON_NOT_FOUND.name(), "Ma khong ton tai");
        }
        Coupon coupon = found.get();
        Instant now = clock.instant();

        if (!Boolean.TRUE.equals(coupon.getActive()) || !coupon.isWithinPeriod(now)) {
            return ValidateResponse.invalid(ErrorCode.COUPON_EXPIRED.name(), "Ma khong con hieu luc");
        }
        if (!coupon.hasQuotaLeft()) {
            return ValidateResponse.invalid(ErrorCode.COUPON_USED_UP.name(), "Ma da het luot su dung");
        }
        if (!coupon.meetsMinimum(subtotal)) {
            return ValidateResponse.invalid(ErrorCode.COUPON_MIN_ORDER_NOT_MET.name(),
                    "Don toi thieu " + coupon.getMinOrderAmount().toPlainString());
        }
        if (redemptions.countByCouponAndUser(coupon.getId(), userId) >= coupon.getPerUserLimit()) {
            return ValidateResponse.invalid(ErrorCode.COUPON_USER_LIMIT_EXCEEDED.name(),
                    "Ban da dung het luot cho ma nay");
        }
        return ValidateResponse.ok(coupon.discountFor(subtotal));
    }

    /**
     * Tru luot va tra ve so tien giam. Goi TRONG transaction dat hang.
     *
     * <p>Khoa dong coupon bang {@code SELECT ... FOR UPDATE} truoc khi kiem va tru:
     * 50 nguoi cung dung mot ma con 1 luot thi dung mot nguoi thang. Day la bai toan
     * dong thoi thu hai cua he thong, dung lai co che da viet cho ton kho.
     */
    @Transactional
    public BigDecimal redeem(String code, BigDecimal subtotal, Long userId, Order order) {
        Coupon coupon = coupons.lockByCode(code)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.COUPON_NOT_FOUND, code));

        Instant now = clock.instant();
        if (!Boolean.TRUE.equals(coupon.getActive()) || !coupon.isWithinPeriod(now)) {
            throw new BusinessException(ErrorCode.COUPON_EXPIRED);
        }
        if (!coupon.meetsMinimum(subtotal)) {
            throw new BusinessException(ErrorCode.COUPON_MIN_ORDER_NOT_MET,
                    "Don toi thieu " + coupon.getMinOrderAmount().toPlainString());
        }
        if (redemptions.countByCouponAndUser(coupon.getId(), userId) >= coupon.getPerUserLimit()) {
            throw new BusinessException(ErrorCode.COUPON_USER_LIMIT_EXCEEDED);
        }
        if (!coupon.hasQuotaLeft()) {
            throw new BusinessException(ErrorCode.COUPON_USED_UP);
        }

        coupon.consume();
        redemptions.save(new CouponRedemption(coupon, users.getReferenceById(userId), order));

        BigDecimal discount = coupon.discountFor(subtotal);
        log.info("Ma {} da duoc dung boi user {} cho don {}: giam {} (luot {}/{})",
                coupon.getCode(), userId, order.getCode(), discount,
                coupon.getUsedCount(), coupon.getUsageLimit());
        return discount;
    }

    /** Hoan luot khi huy don - neu khong, ma giam gia se "boc hoi" theo cac don bi huy. */
    @Transactional
    public void releaseForOrder(Long orderId) {
        for (CouponRedemption redemption : redemptions.findByOrderId(orderId)) {
            coupons.lockByCode(redemption.getCoupon().getCode()).ifPresent(Coupon::release);
            redemptions.delete(redemption);
        }
    }

    // ------------------------------------------------------------------ ADMIN

    @Transactional(readOnly = true)
    public List<CouponResponse> findAll() {
        return coupons.findAll().stream().map(CouponResponse::from).toList();
    }

    @Transactional
    public CouponResponse create(CreateCouponRequest req) {
        if (coupons.existsByCodeIgnoreCase(req.code())) {
            throw new BusinessException(ErrorCode.SLUG_ALREADY_EXISTS, "Ma da ton tai: " + req.code());
        }
        if (req.endsAt().isBefore(req.startsAt())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "endsAt phai sau startsAt");
        }
        Coupon coupon = new Coupon(req.code(), req.type(), req.value(), req.minOrderAmount(),
                req.maxDiscount(), req.usageLimit(), req.perUserLimit(), req.startsAt(), req.endsAt());
        return CouponResponse.from(coupons.save(coupon));
    }

    @Transactional
    public void deactivate(Long id) {
        coupons.findById(id)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.COUPON_NOT_FOUND, id))
                .deactivate();
    }
}
