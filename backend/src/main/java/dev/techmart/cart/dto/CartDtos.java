package dev.techmart.cart.dto;

import dev.techmart.cart.domain.Cart;
import dev.techmart.cart.domain.CartItem;
import dev.techmart.catalog.domain.ProductVariant;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public final class CartDtos {

    /** Giới hạn số lượng mỗi dòng: chặn một request quét sạch kho. */
    public static final int MAX_QUANTITY_PER_ITEM = 10;

    private CartDtos() {
    }

    public record AddItemRequest(
            @NotNull Long variantId,
            @NotNull @Min(1) @Max(MAX_QUANTITY_PER_ITEM) Integer quantity
    ) {}

    public record UpdateItemRequest(
            @NotNull @Min(1) @Max(MAX_QUANTITY_PER_ITEM) Integer quantity
    ) {}

    @Schema(description = "Một dòng trong giỏ hàng")
    public record CartItemResponse(
            Long id,
            Long variantId,
            String sku,
            String productName,
            String variantName,
            String thumbnailUrl,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal lineTotal,
            @Schema(description = "Hàng còn mua được ngay bây giờ") int availableQuantity,
            @Schema(description = "true khi số lượng trong giỏ vượt quá hàng còn lại") boolean exceedsAvailable
    ) {
        public static CartItemResponse from(CartItem item) {
            ProductVariant v = item.getVariant();
            return new CartItemResponse(
                    item.getId(), v.getId(), v.getSku(),
                    v.getProduct().getName(), v.getName(), v.getProduct().getThumbnailUrl(),
                    v.getPrice(), item.getQuantity(), item.lineTotal(),
                    v.availableQuantity(), item.getQuantity() > v.availableQuantity());
        }
    }

    public record CartResponse(
            Long id,
            List<CartItemResponse> items,
            int totalItems,
            BigDecimal subtotal,
            @Schema(description = "true khi có ít nhất một dòng vượt quá hàng còn lại") boolean hasStockIssue
    ) {
        public static CartResponse from(Cart cart) {
            List<CartItemResponse> items = cart.getItems().stream()
                    .map(CartItemResponse::from)
                    .toList();
            return new CartResponse(
                    cart.getId(), items,
                    items.stream().mapToInt(CartItemResponse::quantity).sum(),
                    cart.subtotal(),
                    items.stream().anyMatch(CartItemResponse::exceedsAvailable));
        }
    }
}
