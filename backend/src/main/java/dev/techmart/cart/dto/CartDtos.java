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

    /** Giới hạn số lượng mọi dòng: chặn một request quet sach kho. */
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

    @Schema(description = "Mot dong trong gio hang")
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
            @Schema(description = "Hang con mua duoc ngay bay gio") int availableQuantity,
            @Schema(description = "true khi so luong trong gio vuot qua hang con lai") boolean exceedsAvailable
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
            @Schema(description = "true khi co it nhat mot dong vuot qua hang con lai") boolean hasStockIssue
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
