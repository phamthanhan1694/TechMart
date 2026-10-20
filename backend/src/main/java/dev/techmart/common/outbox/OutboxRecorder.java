package dev.techmart.common.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/** Ghi event vào outbox. Luôn được gọi TRONG transaction nghiệp vụ đang chạy. */
@Service
@RequiredArgsConstructor
public class OutboxRecorder {

    public static final String AGGREGATE_ORDER = "ORDER";
    public static final String AGGREGATE_PAYMENT = "PAYMENT";

    public static final String ORDER_PLACED = "ORDER_PLACED";
    public static final String ORDER_PAID = "ORDER_PAID";
    public static final String ORDER_CANCELLED = "ORDER_CANCELLED";
    public static final String ORDER_SHIPPED = "ORDER_SHIPPED";
    public static final String ORDER_DELIVERED = "ORDER_DELIVERED";

    private final OutboxEventRepository repository;

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void record(String aggregateType, Long aggregateId, String eventType, Map<String, Object> payload) {
        repository.save(new OutboxEvent(aggregateType, aggregateId, eventType, payload));
    }

    public void recordOrderEvent(Long orderId, String eventType, Map<String, Object> payload) {
        record(AGGREGATE_ORDER, orderId, eventType, payload);
    }
}
