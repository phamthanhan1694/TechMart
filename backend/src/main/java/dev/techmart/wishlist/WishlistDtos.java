package dev.techmart.wishlist;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

public final class WishlistDtos {

    private WishlistDtos() {
    }

    public record WishlistItemResponse(
            Long productId,
            String name,
            String slug,
            String thumbnailUrl,
            String brandName,
            BigDecimal minPrice,
            int availableQuantity,
            @Schema(description = "false khi san pham da ngung ban (xoa mem hoac khong con ACTIVE)")
            boolean purchasable,
            Instant addedAt
    ) {}
}
