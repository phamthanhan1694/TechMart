package dev.techmart.promotion.dto;

import dev.techmart.promotion.domain.Coupon;
import dev.techmart.promotion.domain.DiscountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;

public final class CouponDtos {

    private CouponDtos() {
    }

    public record ValidateRequest(
            @NotBlank @Schema(example = "SALE10") String code,
            @NotNull @DecimalMin("0") @Schema(example = "25000000") BigDecimal subtotal
    ) {}

    @Schema(description = "Kết quả kiểm tra mã. Chỉ để hiển thị trước - KHÔNG giữ chỗ.")
    public record ValidateResponse(
            boolean valid,
            BigDecimal discountAmount,
            @Schema(description = "Mã lỗi khi không hợp lệ, vd COUPON_EXPIRED") String reason,
            String message
    ) {
        public static ValidateResponse ok(BigDecimal discount) {
            return new ValidateResponse(true, discount, null, "Mã hợp le");
        }

        public static ValidateResponse invalid(String reason, String message) {
            return new ValidateResponse(false, BigDecimal.ZERO, reason, message);
        }
    }

    public record CreateCouponRequest(
            @NotBlank @Size(max = 50) @Pattern(regexp = "^[A-Z0-9_-]+$",
                    message = "Mã chỉ gồm chữ hoa, số, gạch ngang và gạch dưới") String code,
            @NotNull DiscountType type,
            @NotNull @DecimalMin("0.01") BigDecimal value,
            @DecimalMin("0") BigDecimal minOrderAmount,
            @DecimalMin("0.01") @Schema(description = "Trần giảm giá, chỉ áp dụng cho PERCENT")
            BigDecimal maxDiscount,
            @Min(1) @Schema(description = "Tổng số lượt, bỏ trống = không giới hạn") Integer usageLimit,
            @Min(1) Integer perUserLimit,
            @NotNull Instant startsAt,
            @NotNull Instant endsAt
    ) {}

    public record CouponResponse(
            Long id,
            String code,
            DiscountType type,
            BigDecimal value,
            BigDecimal minOrderAmount,
            BigDecimal maxDiscount,
            Integer usageLimit,
            Integer usedCount,
            Integer perUserLimit,
            Instant startsAt,
            Instant endsAt,
            Boolean active
    ) {
        public static CouponResponse from(Coupon c) {
            return new CouponResponse(c.getId(), c.getCode(), c.getType(), c.getValue(),
                    c.getMinOrderAmount(), c.getMaxDiscount(), c.getUsageLimit(), c.getUsedCount(),
                    c.getPerUserLimit(), c.getStartsAt(), c.getEndsAt(), c.getActive());
        }
    }
}
