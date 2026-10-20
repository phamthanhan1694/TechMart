package dev.techmart.notification;

import dev.techmart.common.outbox.OutboxEvent;
import dev.techmart.common.outbox.OutboxEventHandler;
import dev.techmart.common.outbox.OutboxRecorder;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Biến event vòng đời đơn hàng thành thông báo cho chủ đơn.
 *
 * <p>Tra chủ đơn từ bảng orders thay vì đọc {@code userId} trong payload: chỉ ORDER_PLACED
 * mang userId, các event đổi trạng thái thì không - và thêm userId vào mọi payload chỉ để
 * phục vụ một consumer là để consumer định hình producer.
 */
@Component
@RequiredArgsConstructor
public class OrderNotificationHandler implements OutboxEventHandler {

    private static final Logger log = LoggerFactory.getLogger(OrderNotificationHandler.class);

    private record Template(String title, String body) {}

    private static final Map<String, Template> TEMPLATES = Map.of(
            "ORDER_PLACED", new Template("Đặt hàng thành công",
                    "Đơn %s đã được tạo và đang chờ thanh toán."),
            "ORDER_PAID", new Template("Thanh toán thành công",
                    "Đơn %s đã được thanh toán. Shop đang chuẩn bị hàng cho bạn."),
            "ORDER_PACKED", new Template("Đơn hàng đã đóng gói",
                    "Đơn %s đã đóng gói xong và sắp được bàn giao cho đơn vị vận chuyển."),
            "ORDER_SHIPPED", new Template("Đơn hàng đang được giao",
                    "Đơn %s đang trên đường tới bạn."),
            "ORDER_DELIVERED", new Template("Giao hàng thành công",
                    "Đơn %s đã giao xong. Đánh giá sản phẩm để giúp người mua sau nhé!"),
            "ORDER_CANCELLED", new Template("Đơn hàng đã huỷ",
                    "Đơn %s đã bị huỷ. Nếu đã thanh toán, tiền sẽ được hoàn lại."),
            "ORDER_REFUNDED", new Template("Đã hoàn tiền",
                    "Đơn %s đã được hoàn tiền."));

    private final NamedParameterJdbcTemplate jdbc;
    private final NotificationService notifications;

    @Override
    public boolean supports(OutboxEvent event) {
        return OutboxRecorder.AGGREGATE_ORDER.equals(event.getAggregateType())
                && TEMPLATES.containsKey(event.getEventType());
    }

    @Override
    public void handle(OutboxEvent event) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT user_id, code FROM orders WHERE id = :id", Map.of("id", event.getAggregateId()));
        if (rows.isEmpty()) {
            // Không ném: xem hợp đồng ở OutboxEventHandler
            log.warn("Outbox event {}: không tìm thấy đơn {}, bỏ qua thông báo", event.getId(), event.getAggregateId());
            return;
        }
        long userId = ((Number) rows.get(0).get("user_id")).longValue();
        String code = (String) rows.get(0).get("code");
        Template t = TEMPLATES.get(event.getEventType());

        notifications.createFromEvent(event.getId(), userId, event.getEventType(),
                t.title(), t.body().formatted(code), "/orders/" + code);
    }
}
