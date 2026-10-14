package dev.techmart.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

/**
 * Cấu hinh riêng của ứng dụng, anh xa từ khoi {@code app:} trong application.yml.
 * Dùng record để bắt buộc bất biến và có validate kieu ngay lúc khoi dòng.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        Seed seed,
        Inventory inventory,
        Payment payment,
        Cors cors,
        Cache cache,
        Reporting reporting,
        LoginLockout loginLockout
) {
    public record Jwt(String secret, Duration accessTtl, Duration refreshTtl, String issuer) {}

    public record Seed(boolean enabled) {}

    public record Inventory(Duration reservationTtl, int releaseJobBatchSize) {}

    public record Payment(String webhookSecret) {}

    public record Cors(List<String> allowedOrigins) {}

    /**
     * Bat/tắt cache sản phẩm. Cần tắt được để đó hiệu năng trước/sau -
     * một có cong tac chỉ để đó là thu dạng có.
     */
    public record Cache(boolean productsEnabled, Duration listTtl, Duration detailTtl) {}

    /**
     * Múi giờ chốt ranh giới ngày cho báo cáo. Cửa hàng bán ở Việt Nam: "doanh thu hôm nay"
     * phải tính từ 00:00 giờ Việt Nam, không phải 00:00 UTC (= 07:00 sáng). Xem ADR-0016.
     */
    public record Reporting(ZoneId zone, int maxRangeDays, int exportMaxRows) {}

    /**
     * Khoá đăng nhập tạm thời theo tài khoản: sai {@code maxAttempts} lần trong {@code window}
     * thì khoá {@code lockDuration}. Xem ADR-0012.
     */
    public record LoginLockout(int maxAttempts, Duration window, Duration lockDuration) {}
}
