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
@Tag(name = "Review", description = "Đánh giá sản phẩm - chỉ người đã mua và đã nhận hàng")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/products/{productId}/reviews")
    @SecurityRequirements
    @Operation(summary = "Đánh giá của một sản phẩm")
    public PageResponse<ReviewResponse> byProduct(@PathVariable Long productId,
                                                  @PageableDefault(size = 20) Pageable pageable) {
        return reviewService.findByProduct(productId, PageableSanitizer.sanitize(pageable));
    }

    @GetMapping("/products/{productId}/reviews/summary")
    @SecurityRequirements
    @Operation(summary = "Điểm trung bình và phân bố số sao",
            description = "Tính bằng query GROUP BY ở DB, không nạp hết đánh giá về ứng dụng.")
    public RatingSummary summary(@PathVariable Long productId) {
        return reviewService.summaryFor(productId);
    }

    @PostMapping("/reviews")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Viết đánh giá",
            description = """
                    Chỉ đánh giá được dòng đơn hàng **thuộc về mình** và đơn đã **DELIVERED**.
                    Mỗi dòng đơn chỉ đánh giá một lần - đảm bảo bởi UNIQUE(order_item_id) ở DB,
                    không chỉ bằng câu kiểm trong code.

                    Mua lại lần sau thì đánh giá lại được, vì đó là một lần mua khác.
                    """)
    public ResponseEntity<ReviewResponse> create(@AuthenticationPrincipal AuthPrincipal principal,
                                                 @Valid @RequestBody CreateReviewRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reviewService.create(principal, req));
    }

    @PutMapping("/reviews/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    @Operation(summary = "Sửa đánh giá của mình")
    public ReviewResponse update(@AuthenticationPrincipal AuthPrincipal principal,
                                 @PathVariable Long id,
                                 @Valid @RequestBody UpdateReviewRequest req) {
        return reviewService.update(principal, id, req);
    }

    @DeleteMapping("/reviews/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    @Operation(summary = "Xoá đánh giá", description = "Chủ đánh giá hoặc ADMIN. Của người khác trả 404.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
        reviewService.delete(principal, id);
    }
}
