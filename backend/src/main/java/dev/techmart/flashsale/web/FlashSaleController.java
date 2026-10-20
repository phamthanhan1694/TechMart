package dev.techmart.flashsale.web;

import dev.techmart.common.error.BusinessException;
import dev.techmart.common.error.ErrorCode;
import dev.techmart.flashsale.dto.FlashSaleDtos.*;
import dev.techmart.flashsale.service.FlashSaleService;
import dev.techmart.identity.security.AuthPrincipal;
import dev.techmart.order.dto.OrderDtos.OrderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/flash-sales")
@Tag(name = "Flash Sale", description = "Mở bán giới hạn - trừ tồn bằng Redis Lua nguyên tử")
public class FlashSaleController {

    private final FlashSaleService flashSaleService;

    public FlashSaleController(FlashSaleService flashSaleService) {
        this.flashSaleService = flashSaleService;
    }

    @GetMapping("/active")
    @SecurityRequirements
    @Operation(summary = "Các phiên đang chạy",
            description = "Số suất còn lại đọc từ Redis khi phiên đang chạy, từ DB khi đã kết thúc.")
    public List<FlashSaleResponse> active() {
        return flashSaleService.findActive();
    }

    @GetMapping("/upcoming")
    @SecurityRequirements
    @Operation(summary = "Các phiên sắp diễn ra", description = "Kèm số giây còn lại trước giờ mở bán.")
    public List<FlashSaleResponse> upcoming() {
        return flashSaleService.findUpcoming();
    }

    @PostMapping("/items/{itemId}/purchase")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Mua một suất flash sale",
            description = """
                    **Bắt buộc** header `Idempotency-Key`.

                    Đường đi: rate limit (10 req/phút/user, 100/phút/IP) -> cửa Redis
                    (Lua nguyên tử kiểm tồn + giới hạn/user + trừ trong MỘT lệnh) -> ghi đơn ở DB.
                    Chỉ người thắng ở cửa Redis mới chạm vào DB.

                    Mã lỗi: `409 SOLD_OUT` hết suất, `409 FLASH_SALE_LIMIT_EXCEEDED` vượt giới hạn,
                    `429` vượt rate limit, `503` Redis chết (fail-closed - không đảm bảo được thì không bán).
                    """)
    public ResponseEntity<OrderResponse> purchase(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long itemId,
            @Parameter(description = "UUID do client sinh, bắt buộc", required = true)
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PurchaseRequest req) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED,
                    "Thiếu header Idempotency-Key.");
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(flashSaleService.purchase(principal.id(), itemId, req, idempotencyKey));
    }

    // ------------------------------------------------------------------ ADMIN

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tạo phiên mở bán")
    public ResponseEntity<FlashSaleResponse> create(@Valid @RequestBody CreateFlashSaleRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(flashSaleService.create(req));
    }

    @PostMapping("/{id}/warmup")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Nạp sẵn tồn kho vào Redis trước giờ mở bán",
            description = "Không nạp trước thì đúng giây mở bán sẽ có hàng nghìn request "
                    + "cùng nạp một lúc - cache stampede. Nạp trước là cách chặn rẻ nhất.")
    public Map<String, Object> warmUp(@PathVariable Long id) {
        int items = flashSaleService.warmUp(id);
        return Map.of("flashSaleId", id, "warmedUpItems", items, "status", "RUNNING");
    }

    @PostMapping("/{id}/reconcile")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Đối soát Redis với DB sau khi phiên kết thúc",
            description = "Lệch thì ghi cảnh báo vào log - đây là cái giá phải trả cho nhất quán cuối.")
    public Map<String, String> reconcile(@PathVariable Long id) {
        flashSaleService.reconcile(id);
        return Map.of("status", "Đã đối soát, xem log để biết chi tiết");
    }
}
