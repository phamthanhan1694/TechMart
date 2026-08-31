package dev.techmart.order.web;

import dev.techmart.common.error.BusinessException;
import dev.techmart.common.error.ErrorCode;
import dev.techmart.common.web.PageResponse;
import dev.techmart.common.web.PageableSanitizer;
import dev.techmart.identity.security.AuthPrincipal;
import dev.techmart.order.dto.OrderDtos.*;
import dev.techmart.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Order", description = "Dat hang, xem don, huy don")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Dat hang tu phien checkout",
            description = """
                    **Bat buoc** header `Idempotency-Key` (UUID do client sinh).
                    Goi lai cung key se tra ve dung don da tao, khong tao don thu hai.

                    Toan bo chay trong mot transaction: chot hang da giu -> tru ton kho ->
                    tao don kem snapshot ten/SKU/gia -> don gio hang -> ghi outbox.
                    """)
    public ResponseEntity<OrderResponse> place(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Parameter(description = "UUID do client sinh, bat buoc", required = true)
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PlaceOrderRequest req) {

        requireIdempotencyKey(idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.placeOrder(principal.id(), req, idempotencyKey));
    }

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Danh sach don cua chinh minh")
    public PageResponse<OrderSummaryResponse> myOrders(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {
        return orderService.listMyOrders(principal.id(), PageableSanitizer.sanitize(pageable));
    }

    @GetMapping("/{code}")
    @Operation(summary = "Chi tiet don theo ma",
            description = "Khach chi xem duoc don cua minh; don cua nguoi khac tra 404 (khong phai 403).")
    public OrderResponse get(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String code) {
        return orderService.getByCode(code, principal);
    }

    @GetMapping("/{code}/history")
    @Operation(summary = "Lich su doi trang thai cua don")
    public List<StatusHistoryResponse> history(@AuthenticationPrincipal AuthPrincipal principal,
                                               @PathVariable String code) {
        return orderService.getHistory(code, principal);
    }

    @PostMapping("/{code}/cancel")
    @Operation(summary = "Huy don",
            description = "Idempotent: huy mot don da huy tra 200 va KHONG hoan kho lan hai.")
    public OrderResponse cancel(@AuthenticationPrincipal AuthPrincipal principal,
                                @PathVariable String code,
                                @RequestBody(required = false) @Valid CancelOrderRequest req) {
        return orderService.cancel(code, principal, req);
    }

    private void requireIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED,
                    "Thieu header Idempotency-Key. Client phai sinh mot UUID cho moi lan dat hang.");
        }
    }
}
