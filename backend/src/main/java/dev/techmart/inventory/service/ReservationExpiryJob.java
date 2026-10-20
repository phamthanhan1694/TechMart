package dev.techmart.inventory.service;

import dev.techmart.catalog.domain.ProductVariant;
import dev.techmart.common.config.AppProperties;
import dev.techmart.inventory.domain.InventoryReservation;
import dev.techmart.inventory.repository.InventoryReservationRepository;
import lombok.RequiredArgsConstructor;
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
 * Nhả hàng của các phiên giữ hàng đã quá hạn.
 *
 * <p>Đây là cơ chế <b>bảo đảm</b> của toàn bộ tính năng giữ hàng: Redis có thể chết,
 * app có thể restart giữa chừng, nhưng chừng nào DB còn thì hàng vẫn được nhả đúng.
 * Đổi lại là độ trễ tối đa 30 giây - chấp nhận được, vì hàng nhả muộn 30 giây không hại ai
 * còn hàng kẹt vĩnh viễn thì có (ADR-0004).
 *
 * <p>Quét theo lô có giới hạn. Thao tác nhả hàng phải idempotent: điều kiện
 * {@code status = HELD} được kiểm lại ngay trước khi đổi, nên job chạy chồng vẫn đúng.
 */
@Component
@RequiredArgsConstructor
public class ReservationExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpiryJob.class);

    private final InventoryReservationRepository reservations;
    private final VariantLocker variantLocker;
    private final AppProperties props;
    private final Clock clock;

    /**
     * <b>Chú ý {@code @Transactional} ở đây.</b> Trước đó method này không có annotation và
     * gọi sang {@code releaseExpiredBatch()} trong CÙNG một class - Spring AOP hoạt động qua
     * proxy, nên gọi nội bộ không đi qua proxy và {@code @Transactional} của method được gọi
     * bị bỏ qua hoàn toàn. Kết quả: job ném TransactionRequiredException mỗi 30 giây.
     *
     * <p>Đây là bẫy kinh điển của Spring, và là thứ chỉ lộ ra khi chạy thật chứ không phải
     * khi đọc code. Đặt annotation ở đây thì transaction mở từ vòng ngoài, lời gọi bên trong
     * chỉ đơn giản tham gia vào nó.
     */
    @Scheduled(fixedDelayString = "PT30S", initialDelayString = "PT10S")
    @Transactional
    public void releaseExpired() {
        int released = releaseExpiredBatch();
        if (released > 0) {
            log.info("Đã nhả {} bản ghi giữ hàng quá hạn", released);
        }
    }

    /** Public để test gọi trực tiếp (gọi từ ngoài vào thì proxy hoạt động bình thường). */
    @Transactional
    public int releaseExpiredBatch() {
        List<InventoryReservation> expired = reservations.findExpiredBatch(
                clock.instant(), Limit.of(props.inventory().releaseJobBatchSize()));
        if (expired.isEmpty()) {
            return 0;
        }

        // Gom theo variant trước, rồi khoá một lần theo thứ tự id - giống hệt luồng giữ hàng.
        // Không được chạm vào r.getVariant() trước khi khoá (xem VariantLocker).
        Map<Long, Integer> byVariant = new LinkedHashMap<>();
        for (InventoryReservation r : expired) {
            // Kiểm lại trạng thái: một transaction khác có thể vừa commit nó thành đơn hàng
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
