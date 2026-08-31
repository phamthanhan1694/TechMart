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
        Cors cors
) {
    public record Jwt(String secret, Duration accessTtl, Duration refreshTtl, String issuer) {}

    public record Seed(boolean enabled) {}

    public record Inventory(Duration reservationTtl, int releaseJobBatchSize) {}

    public record Payment(String webhookSecret) {}

    public record Cors(List<String> allowedOrigins) {}
}
