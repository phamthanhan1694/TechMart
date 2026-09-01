package dev.techmart.common.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lam moi cac gauge nghiep vu theo chu ky.
 *
 * <p>Tach thanh bean rieng thay vi de {@code @Scheduled} trong {@link BusinessMetrics}:
 * lich chay la mot moi quan tam khac voi viec dinh nghia metric, va tach ra thi test
 * goi thang {@code refreshGauges()} duoc ma khong dinh den scheduler.
 *
 * <p>{@code @Transactional} dat ngay tren method {@code @Scheduled} - goi noi bo sang
 * method {@code @Transactional} khac trong cung class se khong di qua proxy.
 */
@Component
public class MetricsRefreshJob {

    private static final Logger log = LoggerFactory.getLogger(MetricsRefreshJob.class);

    private final BusinessMetrics metrics;

    public MetricsRefreshJob(BusinessMetrics metrics) {
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "PT15S", initialDelayString = "PT20S")
    @Transactional(readOnly = true)
    public void refresh() {
        try {
            metrics.refreshGauges();
        } catch (RuntimeException e) {
            // Metric hong khong duoc lam sap ung dung
            log.warn("Khong lam moi duoc gauge nghiep vu: {}", e.getMessage());
        }
    }
}
