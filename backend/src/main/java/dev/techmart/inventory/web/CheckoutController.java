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
@Tag(name = "Checkout", description = "Giu hang tam thoi truoc khi thanh toan")
@PreAuthorize("hasRole('CUSTOMER')")
public class CheckoutController {

    private final ReservationService reservationService;

    public CheckoutController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping("/sessions")
    @Operation(summary = "Bat dau checkout va giu hang 10 phut",
            description = """
                    Giu toan bo gio hang. Sau khi goi, `availableQuantity` ma nguoi khac thay
                    se giam ngay, nhung `stockQuantity` chua doi - hang moi bi giu, chua bi tru.

                    Thieu hang tra 409 INSUFFICIENT_STOCK kem `details` liet ke TUNG variant
                    thieu bao nhieu, de client hien thi chinh xac.

                    Het 10 phut ma chua goi POST /orders thi hang tu dong duoc nha.
                    """)
    public ResponseEntity<CheckoutSessionResponse> create(@AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reservationService.hold(principal.id()));
    }

    @GetMapping("/sessions/{sessionId}")
    @Operation(summary = "Xem phien checkout", description = "Kem so giay con lai truoc khi het han.")
    public CheckoutSessionResponse get(@AuthenticationPrincipal AuthPrincipal principal,
                                       @PathVariable String sessionId) {
        return reservationService.getSession(principal.id(), sessionId);
    }

    @DeleteMapping("/sessions/{sessionId}")
    @Operation(summary = "Huy phien checkout", description = "Nha hang ngay lap tuc, khong cho het han.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String sessionId) {
        reservationService.cancel(principal.id(), sessionId);
    }
}
