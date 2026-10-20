package dev.techmart.order;

import dev.techmart.catalog.domain.ProductVariant;
import dev.techmart.common.error.BusinessException;
import dev.techmart.identity.domain.User;
import dev.techmart.inventory.service.ReservationService;
import dev.techmart.order.domain.OrderStatus;
import dev.techmart.order.dto.OrderDtos.PlaceOrderRequest;
import dev.techmart.order.dto.OrderDtos.ShippingAddress;
import dev.techmart.order.repository.OrderRepository;
import dev.techmart.order.service.OrderService;
import dev.techmart.support.DatabaseCleaner;
import dev.techmart.support.IntegrationTestBase;
import dev.techmart.support.TestDataFactory;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test then chốt của cả dự án: nhiều người mua cùng lúc không bao giờ bán quá tồn kho.
 *
 * <p>Đây là thứ chứng minh phần "chống oversell" hoạt động, thay vì chỉ tuyên bố trong README.
 * Chạy bằng {@code mvn verify} (failsafe), không chạy trong {@code mvn test}.
 *
 * <p><b>Lưu ý kỹ thuật:</b> lớp này KHÔNG có {@code @Transactional}. Nếu có, mọi thread sẽ
 * chạy chung một transaction của test và bài test mất hết ý nghĩa - nó sẽ pass kể cả khi
 * không hề có khoá nào.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Đặt hàng đồng thời")
class PlaceOrderConcurrencyIT extends IntegrationTestBase {

    private static final Logger log = LoggerFactory.getLogger(PlaceOrderConcurrencyIT.class);

    private static final int STOCK = 10;
    private static final int CONCURRENT_BUYERS = 200;
    private static final int THREAD_POOL = 32;

    @Autowired OrderService orderService;
    @Autowired ReservationService reservationService;
    @Autowired OrderRepository orders;
    @Autowired TestDataFactory fixtures;
    @Autowired DatabaseCleaner cleaner;

    @BeforeEach
    void resetDatabase() {
        cleaner.clean();
    }

    @Test
    @DisplayName("200 người cùng mua sản phẩm còn 10 cái -> đúng 10 đơn thành công, tồn kho về 0")
    void placeOrder_concurrent_shouldNeverOversell() throws Exception {
        ProductVariant variant = fixtures.createVariantWithStock(STOCK, "25000000.00");
        Long variantId = variant.getId();

        List<User> buyers = new java.util.ArrayList<>(CONCURRENT_BUYERS);
        for (int i = 0; i < CONCURRENT_BUYERS; i++) {
            buyers.add(fixtures.createCustomerWithCart(variantId, 1));
        }

        ExecutorService pool = Executors.newFixedThreadPool(THREAD_POOL);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(CONCURRENT_BUYERS);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();
        java.util.Map<String, Integer> reasons = new ConcurrentHashMap<>();
        java.util.concurrent.atomic.AtomicBoolean firstFailureLogged = new java.util.concurrent.atomic.AtomicBoolean();

        for (User buyer : buyers) {
            pool.submit(() -> {
                try {
                    startGate.await();                       // thả tất cả cùng một lúc
                    var session = reservationService.hold(buyer.getId());
                    orderService.placeOrder(buyer.getId(), request(session.sessionId()),
                            UUID.randomUUID().toString());
                    succeeded.incrementAndGet();
                } catch (BusinessException e) {
                    rejected.incrementAndGet();              // hết hàng / xung đột -> dùng như mong đổi
                    reasons.merge(e.code() + ": " + e.getMessage(), 1, Integer::sum);
                } catch (Exception e) {
                    // Khoá bi quan có thể ném lỗi hạ tầng khi tranh chấp cao; vẫn là "không bán được"
                    rejected.incrementAndGet();
                    reasons.merge(e.getClass().getSimpleName(), 1, Integer::sum);
                    if (firstFailureLogged.compareAndSet(false, true)) {
                        log.error(">>> NGOÀI LE ĐẦU TIÊN", e);
                    }
                    if (!isLockContention(e)) {
                        unexpected.incrementAndGet();
                        log.warn("Ngoai le khong mong doi", e);
                    }
                } finally {
                    finished.countDown();
                }
            });
        }

        startGate.countDown();
        assertThat(finished.await(120, TimeUnit.SECONDS))
                .as("Tất cả thread phải kết thúc trong 120 giây").isTrue();
        pool.shutdown();

        ProductVariant after = fixtures.reloadVariant(variantId);
        long ordersCreated = orders.count();

        log.info(">>> Lý do thất bại: {}", reasons);
        log.info(">>> Thành công={} Bị từ chối={} | tồn kho còn={} đang giữ={} | số đơn={}",
                succeeded.get(), rejected.get(),
                after.getStockQuantity(), after.getReservedQuantity(), ordersCreated);

        // Ba góc nhìn khác nhau của cùng một sự thật. Assert một cái là chưa đủ chặt.
        assertThat(succeeded.get())
                .as("Số đơn thành công phải đúng bằng tồn kho ban đầu")
                .isEqualTo(STOCK);
        assertThat(succeeded.get() + rejected.get()).isEqualTo(CONCURRENT_BUYERS);
        assertThat(unexpected.get()).as("Không được có ngoại lệ ngoài dự kiến").isZero();

        assertThat(after.getStockQuantity())
                .as("Tồn kho phải về đúng 0, không âm")
                .isZero();
        assertThat(after.getReservedQuantity())
                .as("Không còn hàng nào bị giữ treo")
                .isZero();
        assertThat(ordersCreated)
                .as("Số bản ghi đơn hàng trong DB phải khớp số lần thành công")
                .isEqualTo(STOCK);
    }

    @Test
    @DisplayName("Huỷ đơn hai lần đồng thời -> chỉ hoàn kho một lần")
    void cancelTwiceConcurrently_shouldRestockOnlyOnce() throws Exception {
        ProductVariant variant = fixtures.createVariantWithStock(5, "1000000.00");
        User buyer = fixtures.createCustomerWithCart(variant.getId(), 2);

        var session = reservationService.hold(buyer.getId());
        var order = orderService.placeOrder(buyer.getId(), request(session.sessionId()),
                UUID.randomUUID().toString());

        assertThat(fixtures.reloadVariant(variant.getId()).getStockQuantity()).isEqualTo(3);

        var principal = new dev.techmart.identity.security.AuthPrincipal(
                buyer.getId(), buyer.getEmail(), dev.techmart.identity.domain.Role.CUSTOMER);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);

        for (int i = 0; i < 2; i++) {
            pool.submit(() -> {
                try {
                    gate.await();
                    orderService.cancel(order.code(), principal, null);
                } catch (Exception ignored) {
                    // Một trong hai có thể thua optimistic lock -> đúng như thiết kế
                } finally {
                    done.countDown();
                }
            });
        }
        gate.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        ProductVariant after = fixtures.reloadVariant(variant.getId());
        assertThat(after.getStockQuantity())
                .as("Hoàn kho đúng một lan: 3 + 2 = 5, không phải 7")
                .isEqualTo(5);
        assertThat(orders.findByCode(order.code()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
    }

    private PlaceOrderRequest request(String sessionId) {
        return new PlaceOrderRequest(sessionId,
                new ShippingAddress("Nguyễn Văn A", "0901234567", "12 Nguyen Trai",
                        "Ben Thanh", "Quận 1", "TP Hồ Chí Minh"),
                "Giao gio hanh chinh", null);
    }

    private static boolean isLockContention(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String name = t.getClass().getName();
            if (name.contains("Lock") || name.contains("Deadlock")
                    || name.contains("CannotAcquire") || name.contains("Timeout")
                    || name.contains("DataIntegrityViolation") || name.contains("ConstraintViolation")) {
                return true;
            }
        }
        return false;
    }
}
