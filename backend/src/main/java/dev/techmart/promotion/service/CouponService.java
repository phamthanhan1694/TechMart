package dev.techmart.promotion.service;

import dev.techmart.audit.domain.AuditAction;
import dev.techmart.audit.service.AuditLogger;

import dev.techmart.common.error.BusinessException;
import dev.techmart.common.error.ErrorCode;
import dev.techmart.identity.repository.UserRepository;
import dev.techmart.order.domain.Order;
import dev.techmart.promotion.domain.Coupon;
import dev.techmart.promotion.domain.CouponRedemption;
import dev.techmart.promotion.dto.CouponDtos.*;
import dev.techmart.promotion.repository.CouponRedemptionRepository;
import dev.techmart.promotion.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CouponService {

    private static final Logger log = LoggerFactory.getLogger(CouponService.class);

    private final CouponRepository coupons;
    private final CouponRedemptionRepository redemptions;
    private final UserRepository users;
    private final AuditLogger audit;
    private final Clock clock;

    /**
     * Kiểm tra mã để hiển thị trước cho khách.
     *
     * <p><b>Không giữ chỗ.</b> Việc trừ lượt thật sự chỉ xảy ra trong transaction đặt hàng,
     * có khoá dòng. Khách thấy "hợp lệ" rồi lúc đặt bị COUPON_USED_UP là hành vi đúng,
     * không phải bug - đặt chỗ theo mã giảm giá sẽ khoá lượt cho những giỏ hàng bị bỏ quên.
     */
    @Transactional(readOnly = true)
    public ValidateResponse validate(String code, BigDecimal subtotal, Long userId) {
        var found = coupons.findByCodeIgnoreCase(code);
        if (found.isEmpty()) {
            return ValidateResponse.invalid(ErrorCode.COUPON_NOT_FOUND.name(), "Mà không tồn tại");
        }
        Coupon coupon = found.get();
        Instant now = clock.instant();

        if (!Boolean.TRUE.equals(coupon.getActive()) || !coupon.isWithinPeriod(now)) {
            return ValidateResponse.invalid(ErrorCode.COUPON_EXPIRED.name(), "Mà không còn hieu lúc");
        }
        if (!coupon.hasQuotaLeft()) {
            return ValidateResponse.invalid(ErrorCode.COUPON_USED_UP.name(), "Mã đã hết lượt sử dụng");
        }
        if (!coupon.meetsMinimum(subtotal)) {
            return ValidateResponse.invalid(ErrorCode.COUPON_MIN_ORDER_NOT_MET.name(),
                    "Đơn tối thiểu " + coupon.getMinOrderAmount().toPlainString());
        }
        if (redemptions.countByCouponAndUser(coupon.getId(), userId) >= coupon.getPerUserLimit()) {
            return ValidateResponse.invalid(ErrorCode.COUPON_USER_LIMIT_EXCEEDED.name(),
                    "Bạn đã dùng hết lượt cho mã này");
        }
        return ValidateResponse.ok(coupon.discountFor(subtotal));
    }

    /**
     * Trừ lượt và trả về số tiền giảm. Gọi TRONG transaction đặt hàng.
     *
     * <p>Khoá dòng coupon bằng {@code SELECT ... FOR UPDATE} trước khi kiểm và trừ:
     * 50 người cùng dùng một mã còn 1 lượt thì đúng một người thắng. Đây là bài toán
     * đồng thời thứ hai của hệ thống, dùng lại cơ chế đã viết cho tồn kho.
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
                    "Đơn tối thiểu " + coupon.getMinOrderAmount().toPlainString());
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
        log.info("Mã {} đã được dùng bởi user {} cho đơn {}: giảm {} (lượt {}/{})",
                coupon.getCode(), userId, order.getCode(), discount,
                coupon.getUsedCount(), coupon.getUsageLimit());
        return discount;
    }

    /** Hoàn lượt khi huỷ đơn - nếu không, mã giảm giá sẽ "bốc hơi" theo các đơn bị huỷ. */
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
            throw new BusinessException(ErrorCode.SLUG_ALREADY_EXISTS, "Mã đã tồn tại: " + req.code());
        }
        if (req.endsAt().isBefore(req.startsAt())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "endsAt phải sau startsAt");
        }
        Coupon coupon = new Coupon(req.code(), req.type(), req.value(), req.minOrderAmount(),
                req.maxDiscount(), req.usageLimit(), req.perUserLimit(), req.startsAt(), req.endsAt());
        Coupon saved = coupons.save(coupon);
        audit.record(AuditAction.COUPON_CREATED, "COUPON", saved.getId(), java.util.Map.of(
                "code", saved.getCode(), "type", req.type().name(), "value", req.value().toPlainString()));
        return CouponResponse.from(saved);
    }

    @Transactional
    public void deactivate(Long id) {
        Coupon coupon = coupons.findById(id)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.COUPON_NOT_FOUND, id));
        coupon.deactivate();
        audit.record(AuditAction.COUPON_DEACTIVATED, "COUPON", id, java.util.Map.of("code", coupon.getCode()));
    }
}
