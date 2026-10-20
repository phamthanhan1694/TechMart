package dev.techmart.notification;

import dev.techmart.catalog.domain.ProductVariant;
import dev.techmart.common.error.BusinessException;
import dev.techmart.common.error.ErrorCode;
import dev.techmart.common.outbox.OutboxEvent;
import dev.techmart.common.outbox.OutboxEventRepository;
import dev.techmart.common.outbox.OutboxWorker;
import dev.techmart.identity.domain.Role;
import dev.techmart.identity.domain.User;
import dev.techmart.identity.security.AuthPrincipal;
import dev.techmart.inventory.service.ReservationService;
import dev.techmart.order.dto.OrderDtos.*;
import dev.techmart.order.service.OrderService;
import dev.techmart.support.DatabaseCleaner;
import dev.techmart.support.IntegrationTestBase;
import dev.techmart.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Thông báo sinh từ outbox - ADR-0013.
 *
 * <p>Điểm cần chứng minh: outbox giao at-least-once, nhưng khách không bao giờ nhận hai
 * thông báo cho cùng một sự kiện.
 */
@DisplayName("Thông báo trong ứng dụng")
class NotificationTest extends IntegrationTestBase {

    @Autowired NotificationService notifications;
    @Autowired OrderNotificationHandler handler;
    @Autowired OutboxWorker outboxWorker;
    @Autowired OutboxEventRepository outboxEvents;
    @Autowired OrderService orderService;
    @Autowired ReservationService reservationService;
    @Autowired TransactionTemplate tx;
    @Autowired TestDataFactory fixtures;
    @Autowired DatabaseCleaner cleaner;

    private User buyer;
    private AuthPrincipal principal;
    private OrderResponse order;

    @BeforeEach
    void setUp() {
        cleaner.clean();
        ProductVariant variant = fixtures.createVariantWithStock(10, "9000000.00");
        buyer = fixtures.createCustomerWithCart(variant.getId(), 1);
        principal = new AuthPrincipal(buyer.getId(), buyer.getEmail(), Role.CUSTOMER);

        var session = reservationService.hold(buyer.getId());
        order = orderService.placeOrder(buyer.getId(), new PlaceOrderRequest(session.sessionId(),
                new ShippingAddress("Nguyễn Văn A", "0901234567", "12 Nguyen Trai", "Ben Thanh", "Quận 1", "TP HCM"),
                null, null), UUID.randomUUID().toString());
    }

    @Test
    @DisplayName("Đặt hàng -> outbox -> một thông báo cho chủ đơn, có link tới đơn")
    void orderPlaced_createsNotification() {
        drainOutbox();

        assertThat(notifications.unreadCount(buyer.getId())).isEqualTo(1);
        assertThat(notifications.list(buyer.getId(), 0, 10).content()).singleElement().satisfies(n -> {
            assertThat(n.type()).isEqualTo("ORDER_PLACED");
            assertThat(n.link()).isEqualTo("/orders/" + order.code());
            assertThat(n.body()).contains(order.code());
            assertThat(n.read()).isFalse();
        });
    }

    @Test
    @DisplayName("Cùng một event xử lý lại (at-least-once) -> vẫn chỉ một thông báo")
    void redeliveredEvent_doesNotDuplicate() {
        drainOutbox();
        OutboxEvent event = outboxEvents.findAll().get(0);

        // Mô phỏng worker chết sau khi gửi nhưng trước khi commit -> event được giao lại
        tx.executeWithoutResult(s -> handler.handle(event));
        tx.executeWithoutResult(s -> handler.handle(event));

        assertThat(notifications.list(buyer.getId(), 0, 10).totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("Huỷ đơn -> thêm thông báo; đánh dấu đã đọc; người khác không đọc được")
    void cancel_markRead_andOwnership() {
        orderService.cancel(order.code(), principal, new CancelOrderRequest("Doi y"));
        drainOutbox();

        var list = notifications.list(buyer.getId(), 0, 10).content();
        assertThat(list).extracting(NotificationService.NotificationResponse::type)
                .containsExactly("ORDER_CANCELLED", "ORDER_PLACED");   // mới nhất đứng đầu

        User stranger = fixtures.createCustomer();
        assertThatThrownBy(() -> notifications.markRead(stranger.getId(), list.get(0).id()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);

        notifications.markRead(buyer.getId(), list.get(0).id());
        assertThat(notifications.unreadCount(buyer.getId())).isEqualTo(1);
        assertThat(notifications.markAllRead(buyer.getId())).isEqualTo(1);
        assertThat(notifications.unreadCount(buyer.getId())).isZero();
    }

    /**
     * Xả outbox tới khi không còn event PENDING.
     *
     * <p>Job @Scheduled của OutboxWorker cũng chạy trong test. Nếu nó đang giữ khoá lô event
     * ({@code SKIP LOCKED}) thì lần drainOnce() của test bỏ qua lô đó và trả về ngay - kiểm
     * "đã hết PENDING" (dữ liệu đã commit) mới là điều kiện cho đúng, không phải số lần gọi.
     */
    private void drainOutbox() {
        for (int i = 0; i < 50; i++) {
            outboxWorker.drainOnce();
            if (outboxEvents.countByStatus(OutboxEvent.Status.PENDING) == 0) return;
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        throw new AssertionError("Outbox khong xa het sau 5 giay");
    }
}
