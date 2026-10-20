package dev.techmart.cart.web;

import dev.techmart.cart.dto.CartDtos.*;
import dev.techmart.cart.service.CartService;
import dev.techmart.identity.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cart")
@Tag(name = "Cart", description = "Giỏ hàng của người đang đăng nhập")
@PreAuthorize("hasRole('CUSTOMER')")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    @Operation(summary = "Xem gio hang",
            description = "Trả kèm availableQuantity từng dòng và cờ hasStockIssue "
                    + "để client báo trước khi khách vào checkout.")
    public CartResponse get(@AuthenticationPrincipal AuthPrincipal principal) {
        return cartService.getCart(principal.id());
    }

    @PostMapping("/items")
    @Operation(summary = "Thêm sản phẩm vào giỏ",
            description = "Variant đã có trong giỏ thì cộng dồn số lượng. Thêm vào giỏ KHÔNG giữ hàng.")
    public CartResponse addItem(@AuthenticationPrincipal AuthPrincipal principal,
                                @Valid @RequestBody AddItemRequest req) {
        return cartService.addItem(principal.id(), req);
    }

    @PutMapping("/items/{itemId}")
    @Operation(summary = "Đổi số lượng một dòng")
    public CartResponse updateItem(@AuthenticationPrincipal AuthPrincipal principal,
                                   @PathVariable Long itemId,
                                   @Valid @RequestBody UpdateItemRequest req) {
        return cartService.updateItem(principal.id(), itemId, req);
    }

    @DeleteMapping("/items/{itemId}")
    @Operation(summary = "Xoá một dòng khỏi giỏ")
    public CartResponse removeItem(@AuthenticationPrincipal AuthPrincipal principal,
                                   @PathVariable Long itemId) {
        return cartService.removeItem(principal.id(), itemId);
    }

    @DeleteMapping
    @Operation(summary = "Xoá sạch giỏ hàng")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@AuthenticationPrincipal AuthPrincipal principal) {
        cartService.clear(principal.id());
    }
}
