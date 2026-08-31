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
 * Test then chot cua ca du an: nhieu nguoi mua cung luc khong bao gio ban qua ton kho.
 *
 * <p>Day la thu chung minh phan "chong oversell" hoat dong, thay vi chi tuyen bo trong README.
 * Chay bang {@code mvn verify} (failsafe), khong chay trong {@code mvn test}.
 *
 * <p><b>Luu y ky thuat:</b> lop nay KHONG co {@code @Transactional}. Neu co, moi thread se
 * chay chung mot transaction cua test va bai test mat het y nghia - no se pass ke ca khi
 * khong he co khoa nao.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Dat hang dong thoi")
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
    @DisplayName("200 nguoi cung mua san pham con 10 cai -> dung 10 don thanh cong, ton kho ve 0")
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
                    startGate.await();                       // tha tat ca cung mot luc
                    var session = reservationService.hold(buyer.getId());
                    orderService.placeOrder(buyer.getId(), request(session.sessionId()),
                            UUID.randomUUID().toString());
                    succeeded.incrementAndGet();
                } catch (BusinessException e) {
                    rejected.incrementAndGet();              // het hang / xung dot -> dung nhu mong doi
                    reasons.merge(e.code() + ": " + e.getMessage(), 1, Integer::sum);
                } catch (Exception e) {
                    // Khoa bi quan co the nem loi ha tang khi tranh chap cao; van la "khong ban duoc"
                    rejected.incrementAndGet();
                    reasons.merge(e.getClass().getSimpleName(), 1, Integer::sum);
                    if (firstFailureLogged.compareAndSet(false, true)) {
                        log.error(">>> NGOAI LE DAU TIEN", e);
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
                .as("Tat ca thread phai ket thuc trong 120 giay").isTrue();
        pool.shutdown();

        ProductVariant after = fixtures.reloadVariant(variantId);
        long ordersCreated = orders.count();

        log.info(">>> Ly do that bai: {}", reasons);
        log.info(">>> Thanh cong={} Bi tu choi={} | ton kho con={} dang giu={} | so don={}",
                succeeded.get(), rejected.get(),
                after.getStockQuantity(), after.getReservedQuantity(), ordersCreated);

        // Ba goc nhin khac nhau cua cung mot su that. Assert mot cai la chua du chat.
        assertThat(succeeded.get())
                .as("So don thanh cong phai dung bang ton kho ban dau")
                .isEqualTo(STOCK);
        assertThat(succeeded.get() + rejected.get()).isEqualTo(CONCURRENT_BUYERS);
        assertThat(unexpected.get()).as("Khong duoc co ngoai le ngoai du kien").isZero();

        assertThat(after.getStockQuantity())
                .as("Ton kho phai ve dung 0, khong am")
                .isZero();
        assertThat(after.getReservedQuantity())
                .as("Khong con hang nao bi giu treo")
                .isZero();
        assertThat(ordersCreated)
                .as("So ban ghi don hang trong DB phai khop so lan thanh cong")
                .isEqualTo(STOCK);
    }

    @Test
    @DisplayName("Huy don hai lan dong thoi -> chi hoan kho mot lan")
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
                    // Mot trong hai co the thua optimistic lock -> dung nhu thiet ke
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
                .as("Hoan kho dung MOT lan: 3 + 2 = 5, khong phai 7")
                .isEqualTo(5);
        assertThat(orders.findByCode(order.code()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
    }

    private PlaceOrderRequest request(String sessionId) {
        return new PlaceOrderRequest(sessionId,
                new ShippingAddress("Nguyen Van A", "0901234567", "12 Nguyen Trai",
                        "Ben Thanh", "Quan 1", "TP Ho Chi Minh"),
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
