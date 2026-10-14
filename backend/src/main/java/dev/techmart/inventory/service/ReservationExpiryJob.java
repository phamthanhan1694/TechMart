package dev.techmart.inventory.service;

import dev.techmart.catalog.domain.ProductVariant;
import dev.techmart.common.config.AppProperties;
import dev.techmart.inventory.domain.InventoryReservation;
import dev.techmart.inventory.repository.InventoryReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Nha hàng của các phiên giữ hàng da quá hạn.
 *
 * <p>Đây là có che <b>báo dam</b> của toàn bộ tinh nang giữ hàng: Redis có thể chet,
 * app có thể restart giữa chừng, nhưng chừng nào DB còn thì hàng vẫn được nhả đúng.
 * Đổi lai là đó tre tối đa 30 giây - chap nhận được, vì hàng nha muon 30 giây không hai ai
 * con hàng ket vinh vien thì có (ADR-0004).
 *
 * <p>Quet theo lo có giới hạn. Thao tac nhả hàng phải idempotent: điều kiện
 * {@code status = HELD} được kiểm lai ngay trước khi đổi, nên job chay chống vẫn đúng.
 */
@Component
public class ReservationExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpiryJob.class);

    private final InventoryReservationRepository reservations;
    private final VariantLocker variantLocker;
    private final AppProperties props;
    private final Clock clock;

    public ReservationExpiryJob(InventoryReservationRepository reservations,
                                VariantLocker variantLocker,
                                AppProperties props, Clock clock) {
        this.reservations = reservations;
        this.variantLocker = variantLocker;
        this.props = props;
        this.clock = clock;
    }

    /**
     * <b>Chú ý {@code @Transactional} ở đây.</b> Trước đó method này không có annotation va
     * gọi sang {@code releaseExpiredBatch()} trong CUNG một class - Spring AOP hoạt động qua
     * proxy, nên gọi nội bộ không di qua proxy và {@code @Transactional} của method được gọi
     * bi bỏ qua hoan toan. Kết quả: job ném TransactionRequiredException mỗi 30 giây.
     *
     * <p>Đây là bay kinh điển của Spring, và là thu chỉ lo ra khi chay thật chứ không phải
     * khi doc code. Đặt annotation ở đây thì transaction mo từ vong ngoai, lỗi gọi ben trong
     * chỉ đơn gián tham gia vào nó.
     */
    @Scheduled(fixedDelayString = "PT30S", initialDelayString = "PT10S")
    @Transactional
    public void releaseExpired() {
        int released = releaseExpiredBatch();
        if (released > 0) {
            log.info("Da nha {} ban ghi giu hang qua han", released);
        }
    }

    /** Public để test gọi truc tiep (gọi từ ngoai vào thì proxy hoạt động binh thuong). */
    @Transactional
    public int releaseExpiredBatch() {
        List<InventoryReservation> expired = reservations.findExpiredBatch(
                clock.instant(), Limit.of(props.inventory().releaseJobBatchSize()));
        if (expired.isEmpty()) {
            return 0;
        }

        // Gom theo variant trước, rồi khoá một lan theo thứ tự id - giống hệt luồng giữ hàng.
        // Không được cham vào r.getVariant() trước khi khoá (xem VariantLocker).
        Map<Long, Integer> byVariant = new LinkedHashMap<>();
        for (InventoryReservation r : expired) {
            // Kiểm lai trạng thái: một transaction khác có thể vua commit no thanh đơn hàng
            if (r.isHeld()) {
                byVariant.merge(r.getVariant().getId(), r.getQuantity(), Integer::sum);
            }
        }
        for (ProductVariant v : variantLocker.lockAndRefresh(byVariant.keySet())) {
            v.releaseHold(byVariant.get(v.getId()));
        }
        expired.stream().filter(InventoryReservation::isHeld).forEach(InventoryReservation::expire);

        return expired.size();
    }
}
