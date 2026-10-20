package dev.techmart.promotion;

import dev.techmart.catalog.domain.ProductVariant;
import dev.techmart.identity.domain.User;
import dev.techmart.inventory.service.ReservationService;
import dev.techmart.order.dto.OrderDtos.PlaceOrderRequest;
import dev.techmart.order.dto.OrderDtos.ShippingAddress;
import dev.techmart.order.repository.OrderRepository;
import dev.techmart.order.service.OrderService;
import dev.techmart.promotion.domain.Coupon;
import dev.techmart.promotion.domain.DiscountType;
import dev.techmart.promotion.repository.CouponRedemptionRepository;
import dev.techmart.promotion.repository.CouponRepository;
import dev.techmart.support.DatabaseCleaner;
import dev.techmart.support.IntegrationTestBase;
import dev.techmart.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bài toán đồng thời thứ hai của hệ thống: nhiều người tranh một lượt mã giảm giá.
 *
 * <p>Dùng lại đúng cơ chế khoá đã viết cho tồn kho, nên gần như miễn phí về công sức
 * nhưng là một câu chuyện kỹ thuật riêng khi phỏng vấn.
 */
@DisplayName("Mã giảm giá đồng thời")
class CouponConcurrencyIT extends IntegrationTestBase {

    private static final Logger log = LoggerFactory.getLogger(CouponConcurrencyIT.class);
    private static final int RACERS = 50;

    @Autowired OrderService orderService;
    @Autowired ReservationService reservationService;
    @Autowired OrderRepository orders;
    @Autowired CouponRepository coupons;
    @Autowired CouponRedemptionRepository redemptions;
    @Autowired TestDataFactory fixtures;
    @Autowired DatabaseCleaner cleaner;
    @Autowired Clock clock;

    @BeforeEach
    void resetDatabase() {
        cleaner.clean();
    }

    @Test
    @DisplayName("50 người cùng dùng mã còn 1 lượt -> đúng 1 người được giảm giá")
    void redeemCoupon_concurrent_shouldRespectUsageLimit() throws Exception {
        // Tồn kho đủ rộng để không lẫn với bài toán oversell
        ProductVariant variant = fixtures.createVariantWithStock(RACERS * 2, "10000000.00");
        coupons.save(new Coupon("CHIMOTLUOT", DiscountType.FIXED, new BigDecimal("1000000.00"),
                BigDecimal.ZERO, null, /* usageLimit */ 1, /* perUserLimit */ 1,
                clock.instant().minus(1, ChronoUnit.DAYS),
                clock.instant().plus(1, ChronoUnit.DAYS)));

        List<User> buyers = new ArrayList<>(RACERS);
        for (int i = 0; i < RACERS; i++) {
            buyers.add(fixtures.createCustomerWithCart(variant.getId(), 1));
        }

        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(RACERS);
        AtomicInteger discounted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        for (User buyer : buyers) {
            pool.submit(() -> {
                try {
                    gate.await();
                    var session = reservationService.hold(buyer.getId());
                    var order = orderService.placeOrder(buyer.getId(),
                            new PlaceOrderRequest(session.sessionId(), address(), null, "CHIMOTLUOT"),
                            UUID.randomUUID().toString());
                    if (order.discountAmount().compareTo(BigDecimal.ZERO) > 0) {
                        discounted.incrementAndGet();
                    }
                } catch (Exception e) {
                    rejected.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        gate.countDown();
        assertThat(done.await(90, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        Coupon after = coupons.findByCodeIgnoreCase("CHIMOTLUOT").orElseThrow();
        log.info(">>> Được giảm giá={} Bị từ chối={} | usedCount={} | so bản ghi redemption={}",
                discounted.get(), rejected.get(), after.getUsedCount(), redemptions.count());

        assertThat(discounted.get()).as("Đúng một người được dùng mã").isEqualTo(1);
        assertThat(rejected.get()).isEqualTo(RACERS - 1);
        assertThat(after.getUsedCount()).as("Bộ đếm lượt dùng phải bằng 1").isEqualTo(1);
        assertThat(redemptions.count()).as("Chi một bản ghi sử dụng ma").isEqualTo(1);

        // Quyết định thiết kế: mã hết lượt làm HỎNG CẢ ĐƠN, không phải đặt đơn với giá gốc.
        // Khách gửi mã lên là có ý định được giảm giá; lặng lẽ tính giá đầy rồi trừ tiền
        // tệ hơn nhiều so với báo lỗi 409 COUPON_USED_UP để khách đặt lại.
        assertThat(orders.count())
                .as("49 người kia bị rollback cả đơn, không bị tính giá gốc một cách lặng lẽ")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Huỷ đơn có mã giảm giá -> hoàn lại lượt")
    void cancelOrderWithCoupon_shouldReleaseUsage() {
        ProductVariant variant = fixtures.createVariantWithStock(10, "10000000.00");
        coupons.save(new Coupon("HOANLUOT", DiscountType.FIXED, new BigDecimal("500000.00"),
                BigDecimal.ZERO, null, 1, 1,
                clock.instant().minus(1, ChronoUnit.DAYS),
                clock.instant().plus(1, ChronoUnit.DAYS)));

        User buyer = fixtures.createCustomerWithCart(variant.getId(), 1);
        var session = reservationService.hold(buyer.getId());
        var order = orderService.placeOrder(buyer.getId(),
                new PlaceOrderRequest(session.sessionId(), address(), null, "HOANLUOT"),
                UUID.randomUUID().toString());

        assertThat(coupons.findByCodeIgnoreCase("HOANLUOT").orElseThrow().getUsedCount()).isEqualTo(1);

        var principal = new dev.techmart.identity.security.AuthPrincipal(
                buyer.getId(), buyer.getEmail(), dev.techmart.identity.domain.Role.CUSTOMER);
        orderService.cancel(order.code(), principal, null);

        assertThat(coupons.findByCodeIgnoreCase("HOANLUOT").orElseThrow().getUsedCount())
                .as("Huỷ đơn phải hoàn lại lượt, nếu không mã sẽ bốc hơi theo đơn bị huỷ")
                .isZero();
        assertThat(redemptions.count()).isZero();
    }

    private ShippingAddress address() {
        return new ShippingAddress("Nguyễn Văn A", "0901234567", "12 Nguyen Trai",
                "Ben Thanh", "Quận 1", "TP Hồ Chí Minh");
    }
}
