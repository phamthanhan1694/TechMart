package dev.techmart.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class InventoryDtos {

    private InventoryDtos() {
    }

    @Schema(description = "Chi tiết một variant không đủ hàng - trả trong trường details của lỗi")
    public record StockShortage(
            Long variantId,
            String sku,
            String productName,
            int requested,
            int available
    ) {}

    public record ReservedLine(
            Long variantId,
            String sku,
            String productName,
            String variantName,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal lineTotal
    ) {}

    @Schema(description = "Phiên checkout đang giữ hàng")
    public record CheckoutSessionResponse(
            String sessionId,
            @Schema(description = "Hết thời điểm này mà chưa đặt hàng thì hàng tự động được nhả")
            Instant expiresAt,
            long secondsRemaining,
            List<ReservedLine> items,
            BigDecimal subtotal
    ) {}
}
