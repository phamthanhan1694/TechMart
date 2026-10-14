package dev.techmart.wishlist;

import dev.techmart.identity.security.AuthPrincipal;
import dev.techmart.wishlist.WishlistDtos.WishlistItemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/wishlist")
@Tag(name = "Wishlist", description = "San pham yeu thich. PUT/DELETE deu idempotent.")
@PreAuthorize("hasRole('CUSTOMER')")
public class WishlistController {

    private final WishlistService service;

    public WishlistController(WishlistService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Danh sach yeu thich", description = "Moi nhat dung dau. San pham ngung ban van hien, kem purchasable=false.")
    public List<WishlistItemResponse> list(@AuthenticationPrincipal AuthPrincipal principal) {
        return service.list(principal.id());
    }

    @GetMapping("/ids")
    @Operation(summary = "Chi id san pham trong danh sach", description = "De to trang thai nut tim tren trang danh sach.")
    public List<Long> ids(@AuthenticationPrincipal AuthPrincipal principal) {
        return service.productIds(principal.id());
    }

    @PutMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Them vao yeu thich",
            description = "PUT thay vi POST: goi lai bao nhieu lan ket qua cung nhu mot lan - dung ngu nghia cua nut tim.")
    public void add(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long productId) {
        service.add(principal.id(), productId);
    }

    @DeleteMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Bo khoi yeu thich")
    public void remove(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long productId) {
        service.remove(principal.id(), productId);
    }
}
