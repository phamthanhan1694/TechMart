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
@Tag(name = "Flash Sale", description = "Mo ban gioi han - tru ton bang Redis Lua nguyen tu")
public class FlashSaleController {

    private final FlashSaleService flashSaleService;

    public FlashSaleController(FlashSaleService flashSaleService) {
        this.flashSaleService = flashSaleService;
    }

    @GetMapping("/active")
    @SecurityRequirements
    @Operation(summary = "Cac phien dang chay",
            description = "So suat con lai doc tu Redis khi phien dang chay, tu DB khi da ket thuc.")
    public List<FlashSaleResponse> active() {
        return flashSaleService.findActive();
    }

    @GetMapping("/upcoming")
    @SecurityRequirements
    @Operation(summary = "Cac phien sap dien ra", description = "Kem so giay con lai truoc gio mo ban.")
    public List<FlashSaleResponse> upcoming() {
        return flashSaleService.findUpcoming();
    }

    @PostMapping("/items/{itemId}/purchase")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Mua mot suat flash sale",
            description = """
                    **Bat buoc** header `Idempotency-Key`.

                    Duong di: rate limit (10 req/phut/user, 100/phut/IP) -> cua Redis
                    (Lua nguyen tu kiem ton + gioi han/user + tru trong MOT lenh) -> ghi don o DB.
                    Chi nguoi thang o cua Redis moi cham vao DB.

                    Ma loi: `409 SOLD_OUT` het suat, `409 FLASH_SALE_LIMIT_EXCEEDED` vuot gioi han,
                    `429` vuot rate limit, `503` Redis chet (fail-closed - khong dam bao duoc thi khong ban).
                    """)
    public ResponseEntity<OrderResponse> purchase(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long itemId,
            @Parameter(description = "UUID do client sinh, bat buoc", required = true)
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PurchaseRequest req) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED,
                    "Thieu header Idempotency-Key.");
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(flashSaleService.purchase(principal.id(), itemId, req, idempotencyKey));
    }

    // ------------------------------------------------------------------ ADMIN

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tao phien mo ban")
    public ResponseEntity<FlashSaleResponse> create(@Valid @RequestBody CreateFlashSaleRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(flashSaleService.create(req));
    }

    @PostMapping("/{id}/warmup")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Nap san ton kho vao Redis truoc gio mo ban",
            description = "Khong nap truoc thi dung giay mo ban se co hang nghin request "
                    + "cung nap mot luc - cache stampede. Nap truoc la cach chan re nhat.")
    public Map<String, Object> warmUp(@PathVariable Long id) {
        int items = flashSaleService.warmUp(id);
        return Map.of("flashSaleId", id, "warmedUpItems", items, "status", "RUNNING");
    }

    @PostMapping("/{id}/reconcile")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Doi soat Redis voi DB sau khi phien ket thuc",
            description = "Lech thi ghi canh bao vao log - day la cai gia phai tra cho nhat quan cuoi.")
    public Map<String, String> reconcile(@PathVariable Long id) {
        flashSaleService.reconcile(id);
        return Map.of("status", "Da doi soat, xem log de biet chi tiet");
    }
}
