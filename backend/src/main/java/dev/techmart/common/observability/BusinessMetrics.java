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
 * Metric nghiệp vụ, bên cạnh các metric hạ tầng có sẵn của Actuator.
 *
 * <p>Metric hạ tầng (CPU, heap, connection pool) trả lời câu hỏi "hệ thống có khoẻ không".
 * Metric nghiệp vụ trả lời câu hỏi khác và quan trọng hơn khi vận hành:
 * <i>"có bao nhiêu hàng đang bị giữ treo"</i>, <i>"outbox có bị ùn không"</i>,
 * <i>"có bao nhiêu request bị từ chối vì hết hàng"</i>.
 *
 * <p>Các gauge được cache lại và làm mới theo chu kỳ để mỗi lần Prometheus scrape không
 * bắn một câu COUNT xuống DB.
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
                .description("Số lần request bị từ chối vi không đủ tồn kho")
                .register(registry);
        this.flashSaleSoldOut = Counter.builder("techmart.flashsale.sold_out")
                .description("Số lần request flash sale bị từ chối vi hết suất")
                .register(registry);
        this.rateLimited = Counter.builder("techmart.ratelimit.rejected")
                .description("Số request bị chặn bởi rate limit")
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

    /** Làm mới các gauge. Gọi từ {@link MetricsRefreshJob} để tách lịch chạy khỏi logic. */
    public void refreshGauges() {
        activeReservations.set(reservations.countByStatus(ReservationStatus.HELD));
        pendingOutboxEvents.set(outbox.countByStatus(OutboxEvent.Status.PENDING));
    }
}
