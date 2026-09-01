package dev.techmart.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Cau hinh rieng cua ung dung, anh xa tu khoi {@code app:} trong application.yml.
 * Dung record de bat buoc bat bien va co validate kieu ngay luc khoi dong.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        Seed seed,
        Inventory inventory,
        Payment payment,
        Cors cors,
        Cache cache
) {
    public record Jwt(String secret, Duration accessTtl, Duration refreshTtl, String issuer) {}

    public record Seed(boolean enabled) {}

    public record Inventory(Duration reservationTtl, int releaseJobBatchSize) {}

    public record Payment(String webhookSecret) {}

    public record Cors(List<String> allowedOrigins) {}

    /**
     * Bat/tat cache san pham. Can tat duoc de do hieu nang truoc/sau -
     * mot co cong tac chi de do la thu dang co.
     */
    public record Cache(boolean productsEnabled, Duration listTtl, Duration detailTtl) {}
}
