package dev.techmart.promotion.web;

import dev.techmart.identity.security.AuthPrincipal;
import dev.techmart.promotion.dto.CouponDtos.*;
import dev.techmart.promotion.service.CouponService;
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
@Tag(name = "Promotion", description = "Ma giam gia")
public class CouponController {

    private final CouponService couponService;

    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @PostMapping("/validate")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Kiem tra ma giam gia",
            description = """
                    Chi de hien thi truoc cho khach - **khong giu cho**. Viec tru luot that su
                    xay ra trong transaction dat hang, co khoa dong coupon.

                    Thay "hop le" o day roi luc dat hang bi 409 COUPON_USED_UP la hanh vi dung.
                    """)
    public ValidateResponse validate(@AuthenticationPrincipal AuthPrincipal principal,
                                     @Valid @RequestBody ValidateRequest req) {
        return couponService.validate(req.code(), req.subtotal(), principal.id());
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Danh sach ma giam gia")
    public List<CouponResponse> findAll() {
        return couponService.findAll();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tao ma giam gia")
    public ResponseEntity<CouponResponse> create(@Valid @RequestBody CreateCouponRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(couponService.create(req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Vo hieu hoa ma giam gia", description = "Khong xoa cung - ma da dung nam trong lich su don.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable Long id) {
        couponService.deactivate(id);
    }
}
