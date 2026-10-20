package dev.techmart.inventory.web;

import dev.techmart.identity.security.AuthPrincipal;
import dev.techmart.inventory.dto.InventoryDtos.CheckoutSessionResponse;
import dev.techmart.inventory.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/checkout")
@Tag(name = "Checkout", description = "Giữ hàng tạm thời trước khi thanh toán")
@PreAuthorize("hasRole('CUSTOMER')")
public class CheckoutController {

    private final ReservationService reservationService;

    public CheckoutController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping("/sessions")
    @Operation(summary = "Bắt đầu checkout và giữ hàng 10 phút",
            description = """
                    Giữ toàn bộ giỏ hàng. Sau khi gọi, `availableQuantity` mà người khác thấy
                    sẽ giảm ngay, nhưng `stockQuantity` chưa đổi - hàng mới bị giữ, chưa bị trừ.

                    Thiếu hàng trả 409 INSUFFICIENT_STOCK kèm `details` liệt kê TỪNG variant
                    thiếu bao nhiêu, để client hiển thị chính xác.

                    Hết 10 phút mà chưa gọi POST /orders thì hàng tự động được nhả.
                    """)
    public ResponseEntity<CheckoutSessionResponse> create(@AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reservationService.hold(principal.id()));
    }

    @GetMapping("/sessions/{sessionId}")
    @Operation(summary = "Xem phiên checkout", description = "Kèm số giây còn lại trước khi hết hạn.")
    public CheckoutSessionResponse get(@AuthenticationPrincipal AuthPrincipal principal,
                                       @PathVariable String sessionId) {
        return reservationService.getSession(principal.id(), sessionId);
    }

    @DeleteMapping("/sessions/{sessionId}")
    @Operation(summary = "Huỷ phiên checkout", description = "Nhả hàng ngay lập tức, không chờ hết hạn.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String sessionId) {
        reservationService.cancel(principal.id(), sessionId);
    }
}
