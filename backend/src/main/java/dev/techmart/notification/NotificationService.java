package dev.techmart.notification;

import dev.techmart.common.error.BusinessException;
import dev.techmart.common.error.ErrorCode;
import dev.techmart.common.web.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Thông báo trong ứng dụng.
 *
 * <p>Client lấy thông báo bằng <b>polling</b> {@code /unread-count} mỗi 30 giây, không dùng
 * WebSocket/SSE (ADR-0013). Kết nối giữ lâu cần sticky session hoặc pub/sub giữa các
 * instance; polling thì mọi instance trả lời được, và câu đếm chạy trên partial index
 * {@code WHERE read_at IS NULL} nên rẻ. Đổi lại: thông báo tới chậm tối đa 30 giây + chu kỳ
 * outbox (5 giây) - chấp nhận được cho "đơn đã giao", không chấp nhận được cho chat.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public record NotificationResponse(
            Long id, String type, String title, String body, String link, boolean read, Instant createdAt) {}

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(Long userId, int page, int size) {
        long total = Objects.requireNonNull(jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE user_id = :userId", Map.of("userId", userId), Long.class));
        List<NotificationResponse> rows = jdbc.query("""
                SELECT id, type, title, body, link, read_at, created_at
                  FROM notifications
                 WHERE user_id = :userId
                 ORDER BY created_at DESC, id DESC
                 LIMIT :limit OFFSET :offset
                """, new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("limit", size)
                        .addValue("offset", (long) page * size),
                ROW_MAPPER);
        int totalPages = (int) ((total + size - 1) / size);
        return new PageResponse<>(rows, page, size, total, totalPages, (long) (page + 1) * size < total);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return Objects.requireNonNull(jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE user_id = :userId AND read_at IS NULL",
                Map.of("userId", userId), Long.class));
    }

    /** Idempotent; thông báo của người khác trả 404 như mọi tài nguyên sở hữu. */
    @Transactional
    public void markRead(Long userId, Long id) {
        Boolean exists = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM notifications WHERE id = :id AND user_id = :userId)",
                Map.of("id", id, "userId", userId), Boolean.class);
        if (!Boolean.TRUE.equals(exists)) {
            throw BusinessException.notFound(ErrorCode.NOTIFICATION_NOT_FOUND, id);
        }
        jdbc.update("UPDATE notifications SET read_at = :now WHERE id = :id AND read_at IS NULL",
                Map.of("id", id, "now", Timestamp.from(clock.instant())));
    }

    @Transactional
    public int markAllRead(Long userId) {
        return jdbc.update("UPDATE notifications SET read_at = :now WHERE user_id = :userId AND read_at IS NULL",
                Map.of("userId", userId, "now", Timestamp.from(clock.instant())));
    }

    /**
     * Tạo thông báo từ một event outbox. {@code ON CONFLICT DO NOTHING} trên
     * (source_event_id, user_id): event tới lần hai thì câu lệnh không làm gì và KHÔNG ném lỗi -
     * ném lỗi ở đây sẽ làm hỏng transaction của cả lô outbox (xem OutboxEventHandler).
     *
     * @return true nếu thật sự tạo mới
     */
    @Transactional
    public boolean createFromEvent(long eventId, long userId, String type, String title, String body, String link) {
        return jdbc.update("""
                INSERT INTO notifications (user_id, type, title, body, link, source_event_id)
                VALUES (:userId, :type, :title, :body, :link, :eventId)
                ON CONFLICT (source_event_id, user_id) DO NOTHING
                """, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("type", type)
                .addValue("title", title)
                .addValue("body", body)
                .addValue("link", link)
                .addValue("eventId", eventId)) == 1;
    }

    private static final RowMapper<NotificationResponse> ROW_MAPPER = (rs, i) -> new NotificationResponse(
            rs.getLong("id"), rs.getString("type"), rs.getString("title"), rs.getString("body"),
            rs.getString("link"), rs.getTimestamp("read_at") != null,
            rs.getTimestamp("created_at").toInstant());
}
