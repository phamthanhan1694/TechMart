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
 * Nha hang cua cac phien giu hang da qua han.
 *
 * <p>Day la co che <b>bao dam</b> cua toan bo tinh nang giu hang: Redis co the chet,
 * app co the restart giua chung, nhung chung nao DB con thi hang van duoc nha dung.
 * Doi lai la do tre toi da 30 giay - chap nhan duoc, vi hang nha muon 30 giay khong hai ai
 * con hang ket vinh vien thi co (ADR-0004).
 *
 * <p>Quet theo lo co gioi han. Thao tac nha hang phai idempotent: dieu kien
 * {@code status = HELD} duoc kiem lai ngay truoc khi doi, nen job chay chong van dung.
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
     * <b>Chu y {@code @Transactional} o day.</b> Truoc do method nay khong co annotation va
     * goi sang {@code releaseExpiredBatch()} trong CUNG mot class - Spring AOP hoat dong qua
     * proxy, nen goi noi bo khong di qua proxy va {@code @Transactional} cua method duoc goi
     * bi bo qua hoan toan. Ket qua: job nem TransactionRequiredException moi 30 giay.
     *
     * <p>Day la bay kinh dien cua Spring, va la thu chi lo ra khi chay that chu khong phai
     * khi doc code. Dat annotation o day thi transaction mo tu vong ngoai, loi goi ben trong
     * chi don gian tham gia vao no.
     */
    @Scheduled(fixedDelayString = "PT30S", initialDelayString = "PT10S")
    @Transactional
    public void releaseExpired() {
        int released = releaseExpiredBatch();
        if (released > 0) {
            log.info("Da nha {} ban ghi giu hang qua han", released);
        }
    }

    /** Public de test goi truc tiep (goi tu ngoai vao thi proxy hoat dong binh thuong). */
    @Transactional
    public int releaseExpiredBatch() {
        List<InventoryReservation> expired = reservations.findExpiredBatch(
                clock.instant(), Limit.of(props.inventory().releaseJobBatchSize()));
        if (expired.isEmpty()) {
            return 0;
        }

        // Gom theo variant truoc, roi khoa mot lan theo thu tu id - giong het luong giu hang.
        // Khong duoc cham vao r.getVariant() truoc khi khoa (xem VariantLocker).
        Map<Long, Integer> byVariant = new LinkedHashMap<>();
        for (InventoryReservation r : expired) {
            // Kiem lai trang thai: mot transaction khac co the vua commit no thanh don hang
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
