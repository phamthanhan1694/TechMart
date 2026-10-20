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
@Tag(name = "Wishlist", description = "Sản phẩm yêu thích. PUT/DELETE đều idempotent.")
@PreAuthorize("hasRole('CUSTOMER')")
public class WishlistController {

    private final WishlistService service;

    public WishlistController(WishlistService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Danh sách yêu thích", description = "Mới nhất đứng đầu. Sản phẩm ngừng bán vẫn hiện, kèm purchasable=false.")
    public List<WishlistItemResponse> list(@AuthenticationPrincipal AuthPrincipal principal) {
        return service.list(principal.id());
    }

    @GetMapping("/ids")
    @Operation(summary = "Chỉ id sản phẩm trong danh sách", description = "Để tô trạng thái nút tim trên trang danh sách.")
    public List<Long> ids(@AuthenticationPrincipal AuthPrincipal principal) {
        return service.productIds(principal.id());
    }

    @PutMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Thêm vào yêu thích",
            description = "PUT thay vì POST: gọi lại bao nhiêu lần kết quả cũng như một lần - đúng ngữ nghĩa của nút tim.")
    public void add(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long productId) {
        service.add(principal.id(), productId);
    }

    @DeleteMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Bỏ khỏi yêu thích")
    public void remove(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long productId) {
        service.remove(principal.id(), productId);
    }
}
