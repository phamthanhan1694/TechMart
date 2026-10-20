package dev.techmart.review;

import dev.techmart.catalog.domain.ProductVariant;
import dev.techmart.common.error.BusinessException;
import dev.techmart.common.error.ErrorCode;
import dev.techmart.identity.domain.Role;
import dev.techmart.identity.domain.User;
import dev.techmart.identity.security.AuthPrincipal;
import dev.techmart.inventory.service.ReservationService;
import dev.techmart.order.domain.OrderStatus;
import dev.techmart.order.dto.OrderDtos.*;
import dev.techmart.order.repository.OrderRepository;
import dev.techmart.order.service.OrderService;
import dev.techmart.review.dto.ReviewDtos.CreateReviewRequest;
import dev.techmart.review.service.ReviewService;
import dev.techmart.support.DatabaseCleaner;
import dev.techmart.support.IntegrationTestBase;
import dev.techmart.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Đánh giá chỉ cho người đã mua và đã nhận hàng (verified purchase).
 *
 * <p>Điểm thiết kế: review gắn với {@code order_item} chứ không phải {@code product},
 * nên "mỗi lần mua đánh giá một lần" trở thành ràng buộc UNIQUE ở DB thay vì một câu if.
 */
@DisplayName("Đánh giá sản phẩm")
class ReviewTest extends IntegrationTestBase {

    @Autowired ReviewService reviewService;
    @Autowired OrderService orderService;
    @Autowired ReservationService reservationService;
    @Autowired OrderRepository orders;
    @Autowired TransactionTemplate txTemplate;
    @Autowired TestDataFactory fixtures;
    @Autowired DatabaseCleaner cleaner;

    private ProductVariant variant;
    private User buyer;
    private AuthPrincipal principal;
    private AuthPrincipal admin;
    private OrderResponse order;

    @BeforeEach
    void setUp() {
        cleaner.clean();
        variant = fixtures.createVariantWithStock(20, "20000000.00");
        buyer = fixtures.createCustomerWithCart(variant.getId(), 1);
        principal = new AuthPrincipal(buyer.getId(), buyer.getEmail(), Role.CUSTOMER);
        admin = new AuthPrincipal(fixtures.createUser(Role.ADMIN).getId(), "admin@test.dev", Role.ADMIN);

        var session = reservationService.hold(buyer.getId());
        order = orderService.placeOrder(buyer.getId(),
                new PlaceOrderRequest(session.sessionId(), address(), null, null),
                UUID.randomUUID().toString());
    }

    @Test
    @DisplayName("Đơn chưa giao -> không đánh giá được")
    void review_beforeDelivered_isRejected() {
        Long itemId = order.items().get(0).id();

        assertThatThrownBy(() -> reviewService.create(principal, new CreateReviewRequest(itemId, 5, "Tot")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.REVIEW_NOT_ALLOWED);
    }

    @Test
    @DisplayName("Đơn đã giao -> đánh giá được, và chỉ một lần")
    void review_afterDelivered_worksOnce() {
        deliver();
        Long itemId = order.items().get(0).id();

        var review = reviewService.create(principal, new CreateReviewRequest(itemId, 5, "May tot"));
        assertThat(review.rating()).isEqualTo(5);
        assertThat(review.verifiedPurchase()).isTrue();
        assertThat(review.reviewerName())
                .as("Chỉ hiện tên rút gọn, không lộ email")
                .doesNotContain("@");

        // Lần hai bị chặn bởi UNIQUE(order_item_id) ở DB
        assertThatThrownBy(() -> reviewService.create(principal, new CreateReviewRequest(itemId, 1, "Doi y")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.REVIEW_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("Đánh giá dòng đơn của người khác -> 404, không phải 403")
    void review_ofAnotherUsersOrderItem_returns404() {
        deliver();
        User stranger = fixtures.createCustomer();
        var strangerPrincipal = new AuthPrincipal(stranger.getId(), stranger.getEmail(), Role.CUSTOMER);
        Long itemId = order.items().get(0).id();

        assertThatThrownBy(() ->
                reviewService.create(strangerPrincipal, new CreateReviewRequest(itemId, 5, "Chua mua")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code().status().value())
                .as("Trả 403 là gián tiếp xác nhận dòng đơn đó tồn tại")
                .isEqualTo(404);
    }

    @Test
    @DisplayName("Tổng hợp điểm: trung bình và phân bố số sao tính bằng GROUP BY")
    void ratingSummary_isAggregatedInDatabase() {
        deliver();
        reviewService.create(principal, new CreateReviewRequest(order.items().get(0).id(), 4, "Kha"));

        var summary = reviewService.summaryFor(variant.getProduct().getId());
        assertThat(summary.total()).isEqualTo(1);
        assertThat(summary.average()).isEqualTo(4.0);
        assertThat(summary.distribution())
                .as("Phân bố phải có đủ 5 mức, kể cả mức bằng 0")
                .hasSize(5)
                .containsEntry(4, 1L)
                .containsEntry(5, 0L);
    }

    @Test
    @DisplayName("ADMIN xoá được đánh giá của người khác, khách thì không")
    void deleteReview_ownerOrAdminOnly() {
        deliver();
        var review = reviewService.create(principal, new CreateReviewRequest(order.items().get(0).id(), 3, "Tam"));

        User stranger = fixtures.createCustomer();
        var strangerPrincipal = new AuthPrincipal(stranger.getId(), stranger.getEmail(), Role.CUSTOMER);
        assertThatThrownBy(() -> reviewService.delete(strangerPrincipal, review.id()))
                .isInstanceOf(BusinessException.class);

        reviewService.delete(admin, review.id());
        assertThat(reviewService.findByProduct(variant.getProduct().getId(), PageRequest.of(0, 10))
                .totalElements()).isZero();
    }

    /**
     * Đưa đơn qua PAID -> PACKED -> SHIPPED -> DELIVERED.
     *
     * <p>Bước sang PAID chỉ SYSTEM được phép (bảng chuyển trạng thái), nên gọi thẳng
     * transitionBySystem trong một transaction để entity còn được quản lý.
     * Các bước sau đi đường bình thường của admin.
     */
    private void deliver() {
        txTemplate.executeWithoutResult(status -> orderService.transitionBySystem(
                orders.findByCode(order.code()).orElseThrow(), OrderStatus.PAID, "test"));
        for (OrderStatus next : new OrderStatus[]{OrderStatus.PACKED, OrderStatus.SHIPPED, OrderStatus.DELIVERED}) {
            orderService.updateStatus(order.code(), admin, new UpdateStatusRequest(next, "test"));
        }
    }

    private static ShippingAddress address() {
        return new ShippingAddress("Nguyễn Văn A", "0901234567", "12 Nguyen Trai",
                "Ben Thanh", "Quận 1", "TP Hồ Chí Minh");
    }
}
