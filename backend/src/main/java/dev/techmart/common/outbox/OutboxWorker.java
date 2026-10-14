package dev.techmart.common.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * Doc outbox và "gửi" thong báo.
 *
 * <p>Trong pham vì project nay, viec gửi được mo phong bằng ghi log - tich hop nhà cung cấp
 * email thật không thêm chieu sau kỹ thuật nao. Cai dạng ke là <i>có che</i>: event được ghi
 * cũng transaction nghiệp vụ, worker xu ly sau khi da commit, có retry và có giới hạn retry.
 */
@Component
public class OutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);
    private static final int BATCH_SIZE = 100;

    private final OutboxEventRepository repository;
    private final List<OutboxEventHandler> handlers;
    private final Clock clock;

    public OutboxWorker(OutboxEventRepository repository, List<OutboxEventHandler> handlers, Clock clock) {
        this.repository = repository;
        this.handlers = handlers;
        this.clock = clock;
    }

    /** {@code @Transactional} phải ở đây - gọi nội bộ sang drainOnce() không di qua proxy. */
    @Scheduled(fixedDelayString = "PT5S", initialDelayString = "PT15S")
    @Transactional
    public void drain() {
        int processed = drainOnce();
        if (processed > 0) {
            log.debug("Outbox: da xu ly {} event", processed);
        }
    }

    @Transactional
    public int drainOnce() {
        List<OutboxEvent> batch = repository.lockPendingBatch(Limit.of(BATCH_SIZE));
        for (OutboxEvent event : batch) {
            try {
                dispatch(event);
                event.markProcessed(clock.instant());
            } catch (RuntimeException e) {
                event.markFailed(e.getMessage());
                log.warn("Outbox event {} that bai lan {}: {}",
                        event.getId(), event.getRetryCount(), e.getMessage());
            }
        }
        return batch.size();
    }

    private void dispatch(OutboxEvent event) {
        // Chỗ tích hợp thật: email, SMS, đẩy sang RabbitMQ... - hiện mô phỏng bằng log.
        log.info("[OUTBOX] {} #{} payload={}", event.getEventType(), event.getAggregateId(), event.getPayload());
        // Thông báo trong ứng dụng là side effect ghi vào CHÍNH DB này, cùng transaction với
        // markProcessed - nên với nó, at-least-once thực chất thành exactly-once.
        for (OutboxEventHandler handler : handlers) {
            if (handler.supports(event)) {
                handler.handle(event);
            }
        }
    }
}
