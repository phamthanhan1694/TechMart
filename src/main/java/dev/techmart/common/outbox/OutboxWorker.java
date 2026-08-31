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
 * Doc outbox va "gui" thong bao.
 *
 * <p>Trong pham vi project nay, viec gui duoc mo phong bang ghi log - tich hop nha cung cap
 * email that khong them chieu sau ky thuat nao. Cai dang ke la <i>co che</i>: event duoc ghi
 * cung transaction nghiep vu, worker xu ly sau khi da commit, co retry va co gioi han retry.
 */
@Component
public class OutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);
    private static final int BATCH_SIZE = 100;

    private final OutboxEventRepository repository;
    private final Clock clock;

    public OutboxWorker(OutboxEventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** {@code @Transactional} phai o day - goi noi bo sang drainOnce() khong di qua proxy. */
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
        // Cho tich hop that: email, SMS, day sang RabbitMQ...
        log.info("[OUTBOX] {} #{} payload={}", event.getEventType(), event.getAggregateId(), event.getPayload());
    }
}
