package dev.techmart.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Thoi gian duoc inject chu khong goi thang Instant.now().
 *
 * <p>Ly do: test het han reservation phai day dong ho len 11 phut, khong duoc Thread.sleep
 * (docs/05-test-plan.md muc 2). Moi cho can "bay gio" deu phai lay tu Clock nay.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
