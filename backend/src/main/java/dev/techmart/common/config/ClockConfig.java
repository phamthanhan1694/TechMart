package dev.techmart.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

/**
 * Thoi gian được inject chứ không gọi thẳng Instant.now().
 *
 * <p>Lý do: test hết hạn reservation phải đây dòng họ len 11 phút, không được Thread.sleep
 * (docs/05-test-plan.md mục 2). Mọi chỗ cần "bây giờ" đều phải lấy từ Clock nay.
 */
@Configuration
public class ClockConfig {

    /**
     * Cat ve đó phần giai mili giây.
     *
     * <p>Lý do: {@code Instant} trong bộ nhớ có đó phần giai nano giây, con PostgreSQL
     * {@code TIMESTAMPTZ} chỉ luu được micro giây. Gia tri ghi ra và giá trị đọc lại vì vay
     * <b>không bằng nhau</b> - vd ghi {@code ...768714500Z} nhưng đọc lại {@code ...768715Z}.
     * Chuyen sai này lo ra trong test webhook idempotent (so sanh paidAt trước và sau khi
     * dữ liệu di qua DB).
     *
     * <p>Giữ nano giây trong bộ nhớ chỉ tạo ra nhưng giá trị không bao giờ round-trip được,
     * nên cắt luôn từ gốc. Mili giây cũng là độ phân giải mà JSON serialize ra.
     */
    @Bean
    public Clock clock() {
        return Clock.tick(Clock.systemUTC(), Duration.ofMillis(1));
    }
}
