package dev.techmart.flashsale;

import dev.techmart.catalog.domain.ProductVariant;
import dev.techmart.common.error.BusinessException;
import dev.techmart.common.error.ErrorCode;
import dev.techmart.flashsale.domain.FlashSale;
import dev.techmart.flashsale.domain.FlashSaleItem;
import dev.techmart.flashsale.dto.FlashSaleDtos.PurchaseRequest;
import dev.techmart.flashsale.repository.FlashSaleItemRepository;
import dev.techmart.flashsale.repository.FlashSaleRepository;
import dev.techmart.flashsale.service.FlashSaleService;
import dev.techmart.flashsale.service.FlashSaleStockCounter;
import dev.techmart.identity.domain.User;
import dev.techmart.order.dto.OrderDtos.ShippingAddress;
import dev.techmart.order.repository.OrderRepository;
import dev.techmart.support.DatabaseCleaner;
import dev.techmart.support.IntegrationTestBase;
import dev.techmart.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

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
 * Flash sale dưới tải đột ngột.
 *
 * <p>Đây là kiến trúc hai tầng: cửa Redis quyết định ai được mua (chịu được vài nghìn
 * request/giây trên cùng một suất), con DB chỉ ghi sổ cái cho những người đã thắng.
 */
@DisplayName("Flash sale dong thoi")
class FlashSaleConcurrencyIT extends IntegrationTestBase {

    private static final Logger log = LoggerFactory.getLogger(FlashSaleConcurrencyIT.class);

    private static final int SALE_QUANTITY = 50;
    private static final int BUYERS = 500;
    private static final int THREADS = 32;

    @Autowired FlashSaleService flashSaleService;
    @Autowired FlashSaleStockCounter stockCounter;
    @Autowired FlashSaleRepository sales;
    @Autowired FlashSaleItemRepository items;
    @Autowired OrderRepository orders;
    @Autowired TestDataFactory fixtures;
    @Autowired DatabaseCleaner cleaner;
    @Autowired TransactionTemplate txTemplate;
    @Autowired Clock clock;

    @BeforeEach
    void resetDatabase() {
        cleaner.clean();
    }

    @Test
    @DisplayName("500 nguoi tranh 50 suat -> dung 50 don, sold_quantity khop tuyet doi")
    void flashSalePurchase_underThunderingHerd_neverOversells() throws Exception {
        ProductVariant variant = fixtures.createVariantWithStock(SALE_QUANTITY * 2, "30000000.00");

        Long itemId = txTemplate.execute(status -> {
            FlashSale sale = new FlashSale("Test flash sale",
                    clock.instant().minus(1, ChronoUnit.MINUTES),
                    clock.instant().plus(1, ChronoUnit.HOURS));
            sale.addItem(new FlashSaleItem(
                    fixtures.reloadVariant(variant.getId()),
                    new BigDecimal("19000000.00"), SALE_QUANTITY, /* perUserLimit */ 1));
            sale.markRunning();
            FlashSale saved = sales.save(sale);
            saved.getItems().forEach(stockCounter::warmUp);   // nạp sẵn tồn kho vào Redis
            return saved.getItems().get(0).getId();
        });

        List<User> buyers = new ArrayList<>(BUYERS);
        for (int i = 0; i < BUYERS; i++) {
            buyers.add(fixtures.createCustomer());
        }

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(BUYERS);
        AtomicInteger sold = new AtomicInteger();
        AtomicInteger soldOut = new AtomicInteger();
        AtomicInteger other = new AtomicInteger();
        java.util.Map<String,Integer> reasons = new ConcurrentHashMap<>();

        for (User buyer : buyers) {
            pool.submit(() -> {
                try {
                    gate.await();
                    flashSaleService.purchase(buyer.getId(), itemId,
                            new PurchaseRequest(1, address()), UUID.randomUUID().toString());
                    sold.incrementAndGet();
                } catch (BusinessException e) {
                    if (e.code() == ErrorCode.SOLD_OUT) {
                        soldOut.incrementAndGet();
                    } else {
                        other.incrementAndGet();
                        reasons.merge("BusinessException:" + e.code(), 1, Integer::sum);
                    }
                } catch (Exception e) {
                    other.incrementAndGet();
                    reasons.merge(e.getClass().getSimpleName() + ": " + e.getMessage(), 1, Integer::sum);
                } finally {
                    done.countDown();
                }
            });
        }
        gate.countDown();
        assertThat(done.await(180, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        FlashSaleItem after = items.findById(itemId).orElseThrow();
        int redisRemaining = stockCounter.remaining(itemId).orElseThrow();
        ProductVariant variantAfter = fixtures.reloadVariant(variant.getId());

        log.info(">>> Ly do that bai khac: {}", reasons);
        log.info(">>> Ban duoc={} Het suat={} Khac={} | sold_quantity(DB)={} ton Redis={} | so don={}",
                sold.get(), soldOut.get(), other.get(),
                after.getSoldQuantity(), redisRemaining, orders.count());

        assertThat(sold.get()).as("Dung bang so suat mo ban").isEqualTo(SALE_QUANTITY);
        assertThat(sold.get() + soldOut.get() + other.get()).isEqualTo(BUYERS);

        // Một số ít request có thể thua tranh chấp khi ghi DB (lock timeout, xung đột version).
        // Khi đó suất của họ được TRẢ LẠI cho Redis và người khác mua được - nên tổng bán ra
        // vẫn đúng bằng SALE_QUANTITY. Người đó nhận lỗi và có thể thử lại.
        // Vì vậy không assert other == 0 (sẽ làm test flaky), mà assert nó phải hiếm.
        assertThat(other.get())
                .as("Loi do tranh chap phai hiem, va khong duoc lam sai tong so ban ra")
                .isLessThanOrEqualTo(BUYERS / 50);

        assertThat(after.getSoldQuantity())
                .as("sold_quantity o DB phai khop so lan ban thanh cong")
                .isEqualTo(SALE_QUANTITY);
        assertThat(redisRemaining)
                .as("Bo dem Redis phai ve 0, khong am")
                .isZero();
        assertThat(orders.count()).isEqualTo(SALE_QUANTITY);

        // Tồn kho thật cũng phải được trừ đúng
        assertThat(variantAfter.getStockQuantity()).isEqualTo(SALE_QUANTITY * 2 - SALE_QUANTITY);
    }

    @Test
    @DisplayName("Vuot gioi han moi nguoi -> 409, khong tru them suat")
    void purchase_beyondPerUserLimit_isRejected() {
        ProductVariant variant = fixtures.createVariantWithStock(100, "10000000.00");
        Long itemId = txTemplate.execute(status -> {
            FlashSale sale = new FlashSale("Gioi han 2 suat",
                    clock.instant().minus(1, ChronoUnit.MINUTES),
                    clock.instant().plus(1, ChronoUnit.HOURS));
            sale.addItem(new FlashSaleItem(fixtures.reloadVariant(variant.getId()),
                    new BigDecimal("7000000.00"), 100, /* perUserLimit */ 2));
            FlashSale saved = sales.save(sale);
            saved.getItems().forEach(stockCounter::warmUp);
            return saved.getItems().get(0).getId();
        });

        User buyer = fixtures.createCustomer();
        flashSaleService.purchase(buyer.getId(), itemId, new PurchaseRequest(2, address()),
                UUID.randomUUID().toString());

        int remainingBefore = stockCounter.remaining(itemId).orElseThrow();

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        flashSaleService.purchase(buyer.getId(), itemId, new PurchaseRequest(1, address()),
                                UUID.randomUUID().toString()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.FLASH_SALE_LIMIT_EXCEEDED);

        assertThat(stockCounter.remaining(itemId).orElseThrow())
                .as("Bi tu choi thi khong duoc tru suat")
                .isEqualTo(remainingBefore);
    }

    @Test
    @DisplayName("Redis chua warmup -> fail-closed, tu choi ban chu khong ban bua")
    void purchase_withoutWarmUp_failsClosed() {
        ProductVariant variant = fixtures.createVariantWithStock(100, "10000000.00");
        Long itemId = txTemplate.execute(status -> {
            FlashSale sale = new FlashSale("Chua warmup",
                    clock.instant().minus(1, ChronoUnit.MINUTES),
                    clock.instant().plus(1, ChronoUnit.HOURS));
            sale.addItem(new FlashSaleItem(fixtures.reloadVariant(variant.getId()),
                    new BigDecimal("7000000.00"), 100, 5));
            return sales.save(sale).getItems().get(0).getId();
        });
        stockCounter.clear(itemId);   // mo phong Redis mất dữ liệu

        User buyer = fixtures.createCustomer();
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        flashSaleService.purchase(buyer.getId(), itemId, new PurchaseRequest(1, address()),
                                UUID.randomUUID().toString()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);

        assertThat(orders.count()).as("Khong duoc tao don nao khi khong dam bao duoc ton kho").isZero();
    }

    private ShippingAddress address() {
        return new ShippingAddress("Nguyen Van A", "0901234567", "12 Nguyen Trai",
                "Ben Thanh", "Quan 1", "TP Ho Chi Minh");
    }
}
