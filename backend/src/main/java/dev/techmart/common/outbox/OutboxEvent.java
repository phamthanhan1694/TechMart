package dev.techmart.common.outbox;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * Su kien cần xu ly bat dòng bo, ghi trong CUNG transaction với thay đổi nghiệp vụ.
 *
 * <p>Vì sao không gọi thẳng {@code mailService.send()} trong transaction đặt hàng:
 * <ul>
 *   <li>gửi mail cham và có thể lỗi -> keo dai transaction, giữ khoá dòng lau hơn cần thiet;</li>
 *   <li>nếu transaction rollback SAU khi mail da gửi thì khách nhận mail ve đơn không tồn tại.</li>
 * </ul>
 * Outbox giai ca hai: event chỉ tồn tại nếu đơn hàng da commit, và viec gửi xay ra sau đó.
 *
 * <p>Đánh đổi thua nhận: giao hàng <i>at-least-once</i> - worker chet sau khi gửi nhưng trước
 * khi commit thì mail gửi trung. Chap nhận được với email; nếu là trừ tiền thì phia nhận
 * phải có khoá idempotent riêng. Xem ADR-0005.
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    public enum Status { PENDING, PROCESSED, FAILED }

    private static final int MAX_RETRY = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected OutboxEvent() {
    }

    public OutboxEvent(String aggregateType, Long aggregateId, String eventType, Map<String, Object> payload) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = Status.PENDING;
        this.retryCount = 0;
    }

    public Long getId() { return id; }
    public String getAggregateType() { return aggregateType; }
    public Long getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public Map<String, Object> getPayload() { return payload; }
    public Status getStatus() { return status; }
    public Integer getRetryCount() { return retryCount; }

    public void markProcessed(Instant now) {
        this.status = Status.PROCESSED;
        this.processedAt = now;
    }

    public void markFailed(String error) {
        this.retryCount++;
        this.lastError = error != null && error.length() > 1000 ? error.substring(0, 1000) : error;
        if (this.retryCount >= MAX_RETRY) {
            this.status = Status.FAILED;   // dùng retry, chờ người xu ly
        }
    }
}
