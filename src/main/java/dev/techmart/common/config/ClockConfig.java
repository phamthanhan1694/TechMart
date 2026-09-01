package dev.techmart.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

/**
 * Thoi gian duoc inject chu khong goi thang Instant.now().
 *
 * <p>Ly do: test het han reservation phai day dong ho len 11 phut, khong duoc Thread.sleep
 * (docs/05-test-plan.md muc 2). Moi cho can "bay gio" deu phai lay tu Clock nay.
 */
@Configuration
public class ClockConfig {

    /**
     * Cat ve do phan giai mili giay.
     *
     * <p>Ly do: {@code Instant} trong bo nho co do phan giai nano giay, con PostgreSQL
     * {@code TIMESTAMPTZ} chi luu duoc micro giay. Gia tri ghi ra va gia tri doc lai vi vay
     * <b>khong bang nhau</b> - vd ghi {@code ...768714500Z} nhung doc lai {@code ...768715Z}.
     * Chuyen sai nay lo ra trong test webhook idempotent (so sanh paidAt truoc va sau khi
     * du lieu di qua DB).
     *
     * <p>Giu nano giay trong bo nho chi tao ra nhung gia tri khong bao gio round-trip duoc,
     * nen cat luon tu goc. Mili giay cung la do phan giai ma JSON serialize ra.
     */
    @Bean
    public Clock clock() {
        return Clock.tick(Clock.systemUTC(), Duration.ofMillis(1));
    }
}
