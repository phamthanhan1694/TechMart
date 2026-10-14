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
 * nên "mỗi lần mua đánh giá một lan" trở thành ràng buộc UNIQUE o DB thay vì một câu if.
 */
@DisplayName("Danh gia san pham")
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
    @DisplayName("Don chua giao -> khong danh gia duoc")
    void review_beforeDelivered_isRejected() {
        Long itemId = order.items().get(0).id();

        assertThatThrownBy(() -> reviewService.create(principal, new CreateReviewRequest(itemId, 5, "Tot")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.REVIEW_NOT_ALLOWED);
    }

    @Test
    @DisplayName("Don da giao -> danh gia duoc, va chi mot lan")
    void review_afterDelivered_worksOnce() {
        deliver();
        Long itemId = order.items().get(0).id();

        var review = reviewService.create(principal, new CreateReviewRequest(itemId, 5, "May tot"));
        assertThat(review.rating()).isEqualTo(5);
        assertThat(review.verifiedPurchase()).isTrue();
        assertThat(review.reviewerName())
                .as("Chi hien ten rut gon, khong lo email")
                .doesNotContain("@");

        // Lần hai bị chặn boi UNIQUE(order_item_id) o DB
        assertThatThrownBy(() -> reviewService.create(principal, new CreateReviewRequest(itemId, 1, "Doi y")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.REVIEW_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("Danh gia dong don cua nguoi khac -> 404, khong phai 403")
    void review_ofAnotherUsersOrderItem_returns404() {
        deliver();
        User stranger = fixtures.createCustomer();
        var strangerPrincipal = new AuthPrincipal(stranger.getId(), stranger.getEmail(), Role.CUSTOMER);
        Long itemId = order.items().get(0).id();

        assertThatThrownBy(() ->
                reviewService.create(strangerPrincipal, new CreateReviewRequest(itemId, 5, "Chua mua")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code().status().value())
                .as("Tra 403 la gian tiep xac nhan dong don do ton tai")
                .isEqualTo(404);
    }

    @Test
    @DisplayName("Tong hop diem: trung binh va phan bo so sao tinh bang GROUP BY")
    void ratingSummary_isAggregatedInDatabase() {
        deliver();
        reviewService.create(principal, new CreateReviewRequest(order.items().get(0).id(), 4, "Kha"));

        var summary = reviewService.summaryFor(variant.getProduct().getId());
        assertThat(summary.total()).isEqualTo(1);
        assertThat(summary.average()).isEqualTo(4.0);
        assertThat(summary.distribution())
                .as("Phan bo phai co du 5 muc, ke ca muc bang 0")
                .hasSize(5)
                .containsEntry(4, 1L)
                .containsEntry(5, 0L);
    }

    @Test
    @DisplayName("ADMIN xoa duoc danh gia cua nguoi khac, khach thi khong")
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
     * <p>Bước sang PAID chỉ SYSTEM được phép (bằng chuyen trạng thái), nên gọi thẳng
     * transitionBySystem trong một transaction để entity còn được quản lý.
     * Các buoc sau di đường bình thường của admin.
     */
    private void deliver() {
        txTemplate.executeWithoutResult(status -> orderService.transitionBySystem(
                orders.findByCode(order.code()).orElseThrow(), OrderStatus.PAID, "test"));
        for (OrderStatus next : new OrderStatus[]{OrderStatus.PACKED, OrderStatus.SHIPPED, OrderStatus.DELIVERED}) {
            orderService.updateStatus(order.code(), admin, new UpdateStatusRequest(next, "test"));
        }
    }

    private static ShippingAddress address() {
        return new ShippingAddress("Nguyen Van A", "0901234567", "12 Nguyen Trai",
                "Ben Thanh", "Quan 1", "TP Ho Chi Minh");
    }
}
