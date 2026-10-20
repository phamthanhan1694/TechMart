package dev.techmart.promotion.web;

import dev.techmart.identity.security.AuthPrincipal;
import dev.techmart.promotion.dto.CouponDtos.*;
import dev.techmart.promotion.service.CouponService;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/coupons")
@Tag(name = "Promotion", description = "Mã giảm giá")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @PostMapping("/validate")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Kiểm tra mã giảm giá",
            description = """
                    Chỉ để hiển thị trước cho khách - **không giữ chỗ**. Việc trừ lượt thật sự
                    xảy ra trong transaction đặt hàng, có khoá dòng coupon.

                    Thấy "hợp lệ" ở đây rồi lúc đặt hàng bị 409 COUPON_USED_UP là hành vi đúng.
                    """)
    public ValidateResponse validate(@AuthenticationPrincipal AuthPrincipal principal,
                                     @Valid @RequestBody ValidateRequest req) {
        return couponService.validate(req.code(), req.subtotal(), principal.id());
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Danh sách mã giảm giá")
    public List<CouponResponse> findAll() {
        return couponService.findAll();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tạo mã giảm giá")
    public ResponseEntity<CouponResponse> create(@Valid @RequestBody CreateCouponRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(couponService.create(req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Vô hiệu hoá mã giảm giá", description = "Không xoá cứng - mã đã dùng nằm trong lịch sử đơn.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable Long id) {
        couponService.deactivate(id);
    }
}
