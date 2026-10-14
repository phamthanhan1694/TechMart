package dev.techmart.review.web;

import dev.techmart.common.web.PageResponse;
import dev.techmart.common.web.PageableSanitizer;
import dev.techmart.identity.security.AuthPrincipal;
import dev.techmart.review.dto.ReviewDtos.*;
import dev.techmart.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Review", description = "Danh gia san pham - chi nguoi da mua va da nhan hang")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/products/{productId}/reviews")
    @SecurityRequirements
    @Operation(summary = "Danh gia cua mot san pham")
    public PageResponse<ReviewResponse> byProduct(@PathVariable Long productId,
                                                  @PageableDefault(size = 20) Pageable pageable) {
        return reviewService.findByProduct(productId, PageableSanitizer.sanitize(pageable));
    }

    @GetMapping("/products/{productId}/reviews/summary")
    @SecurityRequirements
    @Operation(summary = "Diem trung binh va phan bo so sao",
            description = "Tinh bang query GROUP BY o DB, khong nap het danh gia ve ung dung.")
    public RatingSummary summary(@PathVariable Long productId) {
        return reviewService.summaryFor(productId);
    }

    @PostMapping("/reviews")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Viet danh gia",
            description = """
                    Chi danh gia duoc dong don hang **thuoc ve minh** va don da **DELIVERED**.
                    Moi dong don chi danh gia mot lan - dam bao boi UNIQUE(order_item_id) o DB,
                    khong chi bang cau kiem trong code.

                    Mua lai lan sau thi danh gia lai duoc, vi do la mot lan mua khac.
                    """)
    public ResponseEntity<ReviewResponse> create(@AuthenticationPrincipal AuthPrincipal principal,
                                                 @Valid @RequestBody CreateReviewRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reviewService.create(principal, req));
    }

    @PutMapping("/reviews/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    @Operation(summary = "Sua danh gia cua minh")
    public ReviewResponse update(@AuthenticationPrincipal AuthPrincipal principal,
                                 @PathVariable Long id,
                                 @Valid @RequestBody UpdateReviewRequest req) {
        return reviewService.update(principal, id, req);
    }

    @DeleteMapping("/reviews/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    @Operation(summary = "Xoa danh gia", description = "Chu danh gia hoac ADMIN. Cua nguoi khac tra 404.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
        reviewService.delete(principal, id);
    }
}
