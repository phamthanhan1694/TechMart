package dev.techmart.order.web;

import dev.techmart.common.error.BusinessException;
import dev.techmart.common.error.ErrorCode;
import dev.techmart.common.web.PageResponse;
import dev.techmart.common.web.PageableSanitizer;
import dev.techmart.identity.security.AuthPrincipal;
import dev.techmart.order.dto.OrderDtos.*;
import dev.techmart.order.service.OrderService;
import lombok.RequiredArgsConstructor;
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
@Tag(name = "Order", description = "Đặt hàng, xem đơn, huỷ đơn")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Đặt hàng từ phiên checkout",
            description = """
                    **Bắt buộc** header `Idempotency-Key` (UUID do client sinh).
                    Gọi lại cùng key sẽ trả về đúng đơn đã tạo, không tạo đơn thứ hai.

                    Toàn bộ chạy trong một transaction: chốt hàng đã giữ -> trừ tồn kho ->
                    tạo đơn kèm snapshot tên/SKU/giá -> dọn giỏ hàng -> ghi outbox.
                    """)
    public ResponseEntity<OrderResponse> place(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Parameter(description = "UUID do client sinh, bắt buộc", required = true)
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PlaceOrderRequest req) {

        requireIdempotencyKey(idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.placeOrder(principal.id(), req, idempotencyKey));
    }

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Danh sách đơn của chính mình")
    public PageResponse<OrderSummaryResponse> myOrders(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {
        return orderService.listMyOrders(principal.id(), PageableSanitizer.sanitize(pageable));
    }

    @GetMapping("/{code}")
    @Operation(summary = "Chi tiết đơn theo mã",
            description = "Khách chỉ xem được đơn của mình; đơn của người khác trả 404 (không phải 403).")
    public OrderResponse get(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String code) {
        return orderService.getByCode(code, principal);
    }

    @GetMapping("/{code}/history")
    @Operation(summary = "Lịch sử đổi trạng thái của đơn")
    public List<StatusHistoryResponse> history(@AuthenticationPrincipal AuthPrincipal principal,
                                               @PathVariable String code) {
        return orderService.getHistory(code, principal);
    }

    @PostMapping("/{code}/cancel")
    @Operation(summary = "Huỷ đơn",
            description = "Idempotent: huỷ một đơn đã huỷ trả 200 và KHÔNG hoàn kho lần hai.")
    public OrderResponse cancel(@AuthenticationPrincipal AuthPrincipal principal,
                                @PathVariable String code,
                                @RequestBody(required = false) @Valid CancelOrderRequest req) {
        return orderService.cancel(code, principal, req);
    }

    private void requireIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED,
                    "Thiếu header Idempotency-Key. Client phải sinh một UUID cho mỗi lần đặt hàng.");
        }
    }
}
