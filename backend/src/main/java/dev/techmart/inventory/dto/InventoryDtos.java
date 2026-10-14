package dev.techmart.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class InventoryDtos {

    private InventoryDtos() {
    }

    @Schema(description = "Chi tiet mot variant khong du hang - tra trong truong details cua loi")
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

    @Schema(description = "Phien checkout dang giu hang")
    public record CheckoutSessionResponse(
            String sessionId,
            @Schema(description = "Het thoi diem nay ma chua dat hang thi hang tu dong duoc nha")
            Instant expiresAt,
            long secondsRemaining,
            List<ReservedLine> items,
            BigDecimal subtotal
    ) {}
}
