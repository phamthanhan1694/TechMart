package dev.techmart.common.outbox;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * Su kien can xu ly bat dong bo, ghi trong CUNG transaction voi thay doi nghiep vu.
 *
 * <p>Vi sao khong goi thang {@code mailService.send()} trong transaction dat hang:
 * <ul>
 *   <li>gui mail cham va co the loi -> keo dai transaction, giu khoa dong lau hon can thiet;</li>
 *   <li>neu transaction rollback SAU khi mail da gui thi khach nhan mail ve don khong ton tai.</li>
 * </ul>
 * Outbox giai ca hai: event chi ton tai neu don hang da commit, va viec gui xay ra sau do.
 *
 * <p>Danh doi thua nhan: giao hang <i>at-least-once</i> - worker chet sau khi gui nhung truoc
 * khi commit thi mail gui trung. Chap nhan duoc voi email; neu la tru tien thi phia nhan
 * phai co khoa idempotent rieng. Xem ADR-0005.
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
            this.status = Status.FAILED;   // dung retry, cho nguoi xu ly
        }
    }
}
