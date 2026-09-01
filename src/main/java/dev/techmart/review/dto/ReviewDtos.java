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
            @NotNull @Schema(description = "id cua dong don hang - lay tu GET /orders/{code}")
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
            @Schema(description = "Chi hien ten, khong lo email") String reviewerName,
            int rating,
            String comment,
            @Schema(description = "Luon true - chi danh gia duoc san pham da mua va da nhan")
            boolean verifiedPurchase,
            Instant createdAt
    ) {
        public static ReviewResponse from(Review r) {
            return new ReviewResponse(r.getId(), r.getProduct().getId(),
                    maskName(r.getUser().getFullName()), r.getRating(), r.getComment(),
                    true, r.getCreatedAt());
        }

        /** "Nguyen Van A" -> "Nguyen Van A." Che bot ho ten day du la thoi quen tot. */
        private static String maskName(String fullName) {
            if (fullName == null || fullName.isBlank()) {
                return "Khach hang";
            }
            String[] parts = fullName.trim().split("\\s+");
            if (parts.length == 1) {
                return parts[0];
            }
            return parts[0] + " " + parts[parts.length - 1].charAt(0) + ".";
        }
    }

    @Schema(description = "Tong hop danh gia cua mot san pham")
    public record RatingSummary(
            double average,
            long total,
            @Schema(description = "So luot theo tung muc sao, vd {\"5\": 12, \"4\": 3}")
            Map<Integer, Long> distribution
    ) {}
}
