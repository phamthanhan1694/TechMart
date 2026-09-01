package dev.techmart.review.service;

import dev.techmart.common.error.BusinessException;
import dev.techmart.common.error.ErrorCode;
import dev.techmart.common.web.PageResponse;
import dev.techmart.identity.domain.Role;
import dev.techmart.identity.repository.UserRepository;
import dev.techmart.identity.security.AuthPrincipal;
import dev.techmart.order.domain.OrderItem;
import dev.techmart.order.domain.OrderStatus;
import dev.techmart.order.repository.OrderItemRepository;
import dev.techmart.review.domain.Review;
import dev.techmart.review.dto.ReviewDtos.*;
import dev.techmart.review.repository.ReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    private final ReviewRepository reviews;
    private final OrderItemRepository orderItems;
    private final UserRepository users;

    public ReviewService(ReviewRepository reviews, OrderItemRepository orderItems, UserRepository users) {
        this.reviews = reviews;
        this.orderItems = orderItems;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> findByProduct(Long productId, Pageable pageable) {
        return PageResponse.from(
                reviews.findByProductIdOrderByCreatedAtDesc(productId, pageable), ReviewResponse::from);
    }

    @Transactional(readOnly = true)
    public RatingSummary summaryFor(Long productId) {
        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int star = 5; star >= 1; star--) {
            distribution.put(star, 0L);
        }
        long total = 0;
        for (Object[] row : reviews.ratingDistribution(productId)) {
            int star = ((Number) row[0]).intValue();
            long count = ((Number) row[1]).longValue();
            distribution.put(star, count);
            total += count;
        }
        return new RatingSummary(
                Math.round(reviews.averageRating(productId) * 10) / 10.0, total, distribution);
    }

    /**
     * Tao danh gia.
     *
     * <p>Ba dieu kien, kiem theo dung thu tu de thong bao loi co nghia:
     * dong don phai thuoc user hien tai (404 chu khong 403), don phai da DELIVERED,
     * va moi dong don chi danh gia mot lan.
     *
     * <p>Dieu kien thu ba duoc <b>DB bao dam</b> bang UNIQUE(order_item_id), khong chi
     * bang cau kiem o day - hai request song song se cung vuot qua cau kiem.
     */
    @Transactional
    public ReviewResponse create(AuthPrincipal principal, CreateReviewRequest req) {
        OrderItem item = orderItems.findDetailById(req.orderItemId())
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.CART_ITEM_NOT_FOUND, req.orderItemId()));

        // Dong don cua nguoi khac -> 404, khong phai 403
        if (!item.getOrder().isOwnedBy(principal.id())) {
            throw BusinessException.notFound(ErrorCode.CART_ITEM_NOT_FOUND, req.orderItemId());
        }
        if (item.getOrder().getStatus() != OrderStatus.DELIVERED) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_ALLOWED,
                    "Don dang o trang thai " + item.getOrder().getStatus()
                            + ", chi danh gia duoc sau khi da nhan hang");
        }

        Review review = new Review(item.getVariant().getProduct(),
                users.getReferenceById(principal.id()), item, req.rating(), req.comment());
        try {
            return ReviewResponse.from(reviews.saveAndFlush(review));
        } catch (DataIntegrityViolationException e) {
            // UNIQUE(order_item_id) - da danh gia lan mua nay roi
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }
    }

    @Transactional
    public ReviewResponse update(AuthPrincipal principal, Long reviewId, UpdateReviewRequest req) {
        Review review = requireOwnReview(principal, reviewId);
        review.edit(req.rating(), req.comment());
        return ReviewResponse.from(review);
    }

    @Transactional
    public void delete(AuthPrincipal principal, Long reviewId) {
        Review review = requireOwnReview(principal, reviewId);
        reviews.delete(review);
        log.info("User {} da xoa danh gia {}", principal.id(), reviewId);
    }

    private Review requireOwnReview(AuthPrincipal principal, Long reviewId) {
        Review review = reviews.findById(reviewId)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.RESOURCE_NOT_FOUND, reviewId));
        if (principal.role() != Role.ADMIN && !review.isOwnedBy(principal.id())) {
            throw BusinessException.notFound(ErrorCode.RESOURCE_NOT_FOUND, reviewId);
        }
        return review;
    }
}
