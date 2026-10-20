package dev.techmart.flashsale.dto;

import dev.techmart.flashsale.domain.FlashSale;
import dev.techmart.flashsale.domain.FlashSaleItem;
import dev.techmart.flashsale.domain.FlashSaleStatus;
import dev.techmart.order.dto.OrderDtos.ShippingAddress;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class FlashSaleDtos {

    private FlashSaleDtos() {
    }

    public record FlashSaleItemResponse(
            Long id,
            Long variantId,
            String productName,
            String variantName,
            String thumbnailUrl,
            BigDecimal originalPrice,
            BigDecimal salePrice,
            int discountPercent,
            int saleQuantity,
            @Schema(description = "Đọc từ Redis khi phiên đang chạy, từ DB khi đã kết thúc")
            int remaining,
            int perUserLimit
    ) {}

    public record FlashSaleResponse(
            Long id,
            String name,
            Instant startsAt,
            Instant endsAt,
            FlashSaleStatus status,
            long secondsUntilStart,
            List<FlashSaleItemResponse> items
    ) {}

    public record PurchaseRequest(
            @NotNull @Min(1) @Max(5) Integer quantity,
            @NotNull @Valid ShippingAddress shippingAddress
    ) {}

    public record CreateFlashSaleRequest(
            @NotBlank @Size(max = 150) String name,
            @NotNull Instant startsAt,
            @NotNull Instant endsAt,
            @NotEmpty @Valid List<CreateItemRequest> items
    ) {}

    public record CreateItemRequest(
            @NotNull Long variantId,
            @NotNull @DecimalMin("0.01") BigDecimal salePrice,
            @NotNull @Min(1) Integer saleQuantity,
            @Min(1) Integer perUserLimit
    ) {}

    public static FlashSaleItemResponse toItemResponse(FlashSaleItem item, int remaining) {
        BigDecimal original = item.getVariant().getPrice();
        int percent = original.signum() == 0 ? 0
                : original.subtract(item.getSalePrice())
                        .multiply(BigDecimal.valueOf(100))
                        .divide(original, 0, java.math.RoundingMode.DOWN)
                        .intValue();
        return new FlashSaleItemResponse(
                item.getId(), item.getVariant().getId(),
                item.getVariant().getProduct().getName(), item.getVariant().getName(),
                item.getVariant().getProduct().getThumbnailUrl(),
                original, item.getSalePrice(), percent,
                item.getSaleQuantity(), remaining, item.getPerUserLimit());
    }

    public static FlashSaleResponse toResponse(FlashSale sale, List<FlashSaleItemResponse> items, Instant now) {
        long untilStart = Math.max(0, java.time.Duration.between(now, sale.getStartsAt()).toSeconds());
        return new FlashSaleResponse(sale.getId(), sale.getName(), sale.getStartsAt(), sale.getEndsAt(),
                sale.getStatus(), untilStart, items);
    }
}
