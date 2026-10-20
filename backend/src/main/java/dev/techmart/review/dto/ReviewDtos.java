package dev.techmart.review.dto;

import dev.techmart.review.domain.Review;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.Map;

public final class ReviewDtos {

    private ReviewDtos() {
    }

    public record CreateReviewRequest(
            @NotNull @Schema(description = "id của dòng đơn hàng - lấy từ GET /orders/{code}")
            Long orderItemId,
            @NotNull @Min(1) @Max(5) Integer rating,
            @Size(max = 2000) String comment
    ) {}

    public record UpdateReviewRequest(
            @NotNull @Min(1) @Max(5) Integer rating,
            @Size(max = 2000) String comment
    ) {}

    public record ReviewResponse(
            Long id,
            Long productId,
            @Schema(description = "Chỉ hiện tên, không lộ email") String reviewerName,
            int rating,
            String comment,
            @Schema(description = "Luôn true - chỉ đánh giá được sản phẩm đã mua và đã nhận")
            boolean verifiedPurchase,
            Instant createdAt
    ) {
        public static ReviewResponse from(Review r) {
            return new ReviewResponse(r.getId(), r.getProduct().getId(),
                    maskName(r.getUser().getFullName()), r.getRating(), r.getComment(),
                    true, r.getCreatedAt());
        }

        /** "Nguyễn Văn A" -> "Nguyễn Văn A." Che bớt họ tên đầy đủ là thói quen tốt. */
        private static String maskName(String fullName) {
            if (fullName == null || fullName.isBlank()) {
                return "Khách hàng";
            }
            String[] parts = fullName.trim().split("\\s+");
            if (parts.length == 1) {
                return parts[0];
            }
            return parts[0] + " " + parts[parts.length - 1].charAt(0) + ".";
        }
    }

    @Schema(description = "Tổng hợp đánh giá của một sản phẩm")
    public record RatingSummary(
            double average,
            long total,
            @Schema(description = "Số lượt theo từng mức sao, vd {\"5\": 12, \"4\": 3}")
            Map<Integer, Long> distribution
    ) {}
}
