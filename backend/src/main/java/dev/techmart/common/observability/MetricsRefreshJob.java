package dev.techmart.common.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Làm mới các gauge nghiệp vụ theo chu kỳ.
 *
 * <p>Tách thành bean riêng thay vì để {@code @Scheduled} trong {@link BusinessMetrics}:
 * lịch chạy là một mối quan tâm khác với việc định nghĩa metric, và tách ra thì test
 * gọi thẳng {@code refreshGauges()} được mà không dính đến scheduler.
 *
 * <p>{@code @Transactional} đặt ngay trên method {@code @Scheduled} - gọi nội bộ sang
 * method {@code @Transactional} khác trong cùng class sẽ không đi qua proxy.
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
            // Metric hỏng không được làm sập ứng dụng
            log.warn("Không làm mới được gauge nghiệp vụ: {}", e.getMessage());
        }
    }
}
