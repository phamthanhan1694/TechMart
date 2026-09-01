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
 * Metric nghiep vu, ben canh cac metric ha tang co san cua Actuator.
 *
 * <p>Metric ha tang (CPU, heap, connection pool) tra loi cau hoi "he thong co khoe khong".
 * Metric nghiep vu tra loi cau hoi khac va quan trong hon khi van hanh:
 * <i>"co bao nhieu hang dang bi giu treo"</i>, <i>"outbox co bi un khong"</i>,
 * <i>"co bao nhieu request bi tu choi vi het hang"</i>.
 *
 * <p>Cac gauge duoc cache lai va lam moi theo chu ky de moi lan Prometheus scrape khong
 * ban mot cau COUNT xuong DB.
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

    /** Lam moi cac gauge. Goi tu {@link MetricsRefreshJob} de tach lich chay khoi logic. */
    public void refreshGauges() {
        activeReservations.set(reservations.countByStatus(ReservationStatus.HELD));
        pendingOutboxEvents.set(outbox.countByStatus(OutboxEvent.Status.PENDING));
    }
}
