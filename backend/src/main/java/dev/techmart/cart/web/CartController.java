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
@Tag(name = "Cart", description = "Gio hang cua nguoi dang dang nhap")
@PreAuthorize("hasRole('CUSTOMER')")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    @Operation(summary = "Xem gio hang",
            description = "Tra kem availableQuantity tung dong va co hasStockIssue "
                    + "de client bao truoc khi khach vao checkout.")
    public CartResponse get(@AuthenticationPrincipal AuthPrincipal principal) {
        return cartService.getCart(principal.id());
    }

    @PostMapping("/items")
    @Operation(summary = "Them san pham vao gio",
            description = "Variant da co trong gio thi cong don so luong. Them vao gio KHONG giu hang.")
    public CartResponse addItem(@AuthenticationPrincipal AuthPrincipal principal,
                                @Valid @RequestBody AddItemRequest req) {
        return cartService.addItem(principal.id(), req);
    }

    @PutMapping("/items/{itemId}")
    @Operation(summary = "Doi so luong mot dong")
    public CartResponse updateItem(@AuthenticationPrincipal AuthPrincipal principal,
                                   @PathVariable Long itemId,
                                   @Valid @RequestBody UpdateItemRequest req) {
        return cartService.updateItem(principal.id(), itemId, req);
    }

    @DeleteMapping("/items/{itemId}")
    @Operation(summary = "Xoa mot dong khoi gio")
    public CartResponse removeItem(@AuthenticationPrincipal AuthPrincipal principal,
                                   @PathVariable Long itemId) {
        return cartService.removeItem(principal.id(), itemId);
    }

    @DeleteMapping
    @Operation(summary = "Xoa sach gio hang")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@AuthenticationPrincipal AuthPrincipal principal) {
        cartService.clear(principal.id());
    }
}
