package dev.techmart.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

/**
 * Thời gian được inject thay vì gọi trực tiếp Instant.now().
 *
 * <p>Lý do: Khi kiểm thử reservation hết hạn, có thể đẩy
 * thời gian tiến lên 11 phút mà không cần Thread.sleep()
 * (xem docs/05-test-plan.md, mục 2).
 *
 * <p>Mọi nơi cần lấy thời gian hiện tại đều phải sử dụng Clock này.
 */
@Configuration
public class ClockConfig {

    /**
     * Cắt bỏ phần thời gian nhỏ hơn mili giây.
     *
     * <p>Lý do: {@code Instant} trong bộ nhớ có độ phân giải
     * nano giây, còn PostgreSQL {@code TIMESTAMPTZ} chỉ lưu
     * được độ chính xác đến micro giây.
     *
     * <p>Vì vậy, giá trị trước khi ghi và sau khi đọc từ
     * database có thể không bằng nhau.
     *
     * <p>Ví dụ:
     * <ul>
     *   <li>Trước khi ghi: {@code ...768714500Z}</li>
     *   <li>Sau khi đọc: {@code ...768715Z}</li>
     * </ul>
     *
     * <p>Sự khác biệt này có thể xuất hiện trong test webhook
     * idempotent khi so sánh {@code paidAt} trước và sau
     * khi dữ liệu đi qua database.
     *
     * <p>Sử dụng độ phân giải mili giây ngay từ đầu để
     * tránh sai lệch khi ghi và đọc dữ liệu.
     *
     * <p>Lưu ý: JSON có thể biểu diễn độ chính xác cao hơn
     * mili giây tùy theo cấu hình serializer.
     *
     * @return Clock sử dụng múi giờ UTC với độ phân giải mili giây
     */
    @Bean
    public Clock clock() {
        return Clock.tick(Clock.systemUTC(), Duration.ofMillis(1));
    }
}