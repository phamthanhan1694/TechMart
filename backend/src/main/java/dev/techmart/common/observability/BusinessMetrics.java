package dev.techmart.common.observability;

import dev.techmart.common.outbox.OutboxEvent;
import dev.techmart.common.outbox.OutboxEventRepository;
import dev.techmart.inventory.domain.ReservationStatus;
import dev.techmart.inventory.repository.InventoryReservationRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Metric nghiệp vụ, ben canh các metric hạ tầng có sẵn của Actuator.
 *
 * <p>Metric hạ tầng (CPU, heap, connection pool) trả lỗi câu hỏi "hệ thống có khoe không".
 * Metric nghiệp vụ trả lỗi câu hỏi khác và quan trọng hơn khi vận hành:
 * <i>"có bao nhiêu hàng đang bị giữ treo"</i>, <i>"outbox có bị ùn không"</i>,
 * <i>"có bao nhiêu request bị từ chối vì hết hàng"</i>.
 *
 * <p>Các gauge được cache lai và làm mọi theo chu ky để mỗi lần Prometheus scrape không
 * ban một câu COUNT xuong DB.
 */
@Component
public class BusinessMetrics {

    private final AtomicLong activeReservations = new AtomicLong();
    private final AtomicLong pendingOutboxEvents = new AtomicLong();

    private final Counter oversellPrevented;
    private final Counter flashSaleSoldOut;
    private final Counter rateLimited;

    private final InventoryReservationRepository reservations;
    private final OutboxEventRepository outbox;

    public BusinessMetrics(MeterRegistry registry,
                           InventoryReservationRepository reservations,
                           OutboxEventRepository outbox) {
        this.reservations = reservations;
        this.outbox = outbox;

        registry.gauge("techmart.inventory.reservations.active", activeReservations, AtomicLong::get);
        registry.gauge("techmart.outbox.events.pending", pendingOutboxEvents, AtomicLong::get);

        this.oversellPrevented = Counter.builder("techmart.inventory.insufficient_stock")
                .description("So lan request bi tu choi vi khong du ton kho")
                .register(registry);
        this.flashSaleSoldOut = Counter.builder("techmart.flashsale.sold_out")
                .description("So lan request flash sale bi tu choi vi het suat")
                .register(registry);
        this.rateLimited = Counter.builder("techmart.ratelimit.rejected")
                .description("So request bi chan boi rate limit")
                .register(registry);
    }

    public void recordInsufficientStock() {
        oversellPrevented.increment();
    }

    public void recordFlashSaleSoldOut() {
        flashSaleSoldOut.increment();
    }

    public void recordRateLimited() {
        rateLimited.increment();
    }

    /** Làm mới các gauge. Gọi từ {@link MetricsRefreshJob} để tach lịch chạy khoi logic. */
    public void refreshGauges() {
        activeReservations.set(reservations.countByStatus(ReservationStatus.HELD));
        pendingOutboxEvents.set(outbox.countByStatus(OutboxEvent.Status.PENDING));
    }
}
