# Kế hoạch kiểm thử

> `lo-trinh-java-backend.md` mới nêu *chiến lược* test. Tài liệu này nêu **test nào phải có, tên gọi là gì, và viết ra sao** — để khi phỏng vấn bạn mở file test ra chỉ được ngay, không phải nói suông.

---

## 1. Phân bổ công sức

Không đuổi theo con số coverage. Với dự án này, phân bổ hợp lý là:

| Loại | Số lượng mục tiêu | Chiếm gì |
|---|---|---|
| Unit (service, không đụng DB) | ~40 test | Logic tính tiền, luật coupon, bảng chuyển trạng thái, luật review |
| Integration (Testcontainers, DB thật) | ~25 test | Trọn luồng auth → giỏ → checkout → đặt hàng → thanh toán, và mọi truy vấn có JSONB/native SQL |
| **Concurrency** | **5 test** | **Đây là phần ăn điểm. 5 test này quan trọng hơn 200 test còn lại cộng lại** |

Coverage mục tiêu: **75% ở tầng service**, không quan tâm coverage của controller/DTO/entity. Nói được câu này trong phỏng vấn tốt hơn nhiều so với khoe "coverage 95%" mà toàn test getter.

**Không cần test:** getter/setter, entity mapping thuần, DTO record, cấu hình Spring, controller chỉ chuyển tiếp không có logic.

---

## 2. Hạ tầng test

### Testcontainers dùng lại container (bắt buộc)

Mỗi test class khởi động lại PostgreSQL + Redis là cách nhanh nhất để suite test chạy 8 phút và bạn bỏ chạy test. Dùng singleton pattern:

```java
public abstract class IntegrationTestBase {

    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine").withReuse(true);
    static final GenericContainer<?> REDIS =
        new GenericContainer<>("redis:7-alpine").withExposedPorts(6379).withReuse(true);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
}
```

Thêm `testcontainers.reuse.enable=true` vào `~/.testcontainers.properties`. Trên CI thì reuse không có tác dụng — chấp nhận, CI chạy chậm hơn không sao.

### Dọn dữ liệu giữa các test

**Không dùng `@Transactional` trên test class** cho các test concurrency và test luồng đặt hàng — nó khiến mọi thứ chạy trong một transaction bị rollback, và **che mất chính vấn đề đồng thời bạn đang muốn kiểm tra**. Thay bằng `@Sql` truncate trước mỗi test, hoặc một `TestDataCleaner` gọi trong `@BeforeEach`.

Đây là cái bẫy nhiều người dính: concurrency test pass giả vì tất cả thread dùng chung một transaction của test.

### Thời gian có thể điều khiển

Đừng bao giờ `Thread.sleep(600_000)` để chờ reservation hết hạn. Inject `Clock`:

```java
@Bean
Clock clock() { return Clock.systemUTC(); }

// trong test:
@TestConfiguration
static class FixedClockConfig {
    @Bean @Primary
    Clock testClock() { return Clock.fixed(Instant.parse("2026-09-01T10:00:00Z"), UTC); }
}
```

Test hết hạn = đẩy clock lên 11 phút rồi gọi job, không phải ngồi chờ.

---

## 3. Năm test concurrency (phần quan trọng nhất)

### 3.1. `placeOrder_concurrent_shouldNeverOversell()`

Test then chốt của cả dự án.

```java
@Test
void placeOrder_concurrent_shouldNeverOversell() throws Exception {
    Long variantId = seedVariant(/* stock = */ 10);
    int threads = 200;
    var pool = Executors.newFixedThreadPool(32);
    var start = new CountDownLatch(1);
    var done  = new CountDownLatch(threads);
    var ok    = new AtomicInteger();
    var failed = new AtomicInteger();

    for (int i = 0; i < threads; i++) {
        Long userId = seedUserWithCart(variantId, 1);
        pool.submit(() -> {
            try {
                start.await();                        // thả tất cả cùng lúc
                orderService.placeOrder(userId, req());
                ok.incrementAndGet();
            } catch (InsufficientStockException e) {
                failed.incrementAndGet();
            } catch (Exception e) {
                failed.incrementAndGet();
            } finally {
                done.countDown();
            }
        });
    }
    start.countDown();
    assertThat(done.await(60, SECONDS)).isTrue();

    assertThat(ok.get()).isEqualTo(10);              // đúng 10 đơn thành công
    assertThat(failed.get()).isEqualTo(190);
    assertThat(variantRepo.findById(variantId).orElseThrow().getStockQuantity())
        .isZero();                                   // không âm, không dư
    assertThat(orderItemRepo.sumQuantityByVariant(variantId)).isEqualTo(10);
}
```

Ba assertion cuối là ba góc nhìn khác nhau của cùng một sự thật: **số đơn thành công = số hàng đã trừ = số hàng đã bán**. Chỉ assert một cái là chưa đủ chặt.

> **Việc phải làm để lấy điểm:** commit test này **trước** khi thêm khóa, cho nó fail (`ok = 200`, `stock = -190`), commit lại với khóa cho nó pass. Hai commit đó là bằng chứng mạnh nhất trong toàn bộ repo. Ghi vào README kèm số hash commit.

### 3.2. `cancelOrder_calledTwice_shouldRestoreStockOnlyOnce()`

Gọi `cancel` hai lần song song trên cùng một đơn. Tồn kho phải tăng đúng một lần. Đây là lỗi phổ biến nhất trong e-commerce sinh viên.

### 3.3. `applyCoupon_concurrent_shouldRespectUsageLimit()`

Coupon còn 1 lượt, 50 user cùng đặt hàng dùng mã đó → đúng 1 đơn có giảm giá, 49 đơn nhận `COUPON_USED_UP`. Assert thêm `coupon.used_count == 1`.

### 3.4. `flashSalePurchase_concurrent_shouldMatchSaleQuantity()`

50 suất, 500 request đồng thời qua HTTP thật (`MockMvc` hoặc `TestRestTemplate`) → đúng 50 `201`, còn lại `409 SOLD_OUT` hoặc `429`. Assert `sold_quantity == 50` sau khi outbox worker chạy xong.

### 3.5. `reservationExpiry_concurrentWithCheckout_shouldNotDoubleRelease()`

Job quét hết hạn chạy đúng lúc user commit reservation. Chỉ một trong hai được thắng; `reserved_quantity` không được âm và không được trừ hai lần. Test này bắt được lỗi tinh vi nhất trong module reservation.

---

## 4. Danh sách test theo module

### Auth
- `register_duplicateEmail_returns409`
- `login_wrongPassword_returns401_withSameCodeAsUnknownEmail` — chứng minh không rò rỉ email nào tồn tại
- `refresh_rotatesToken_oldTokenBecomesInvalid`
- `refresh_reusedToken_revokesEntireChain` ← test bảo mật quan trọng
- `accessProtectedEndpoint_withoutToken_returns401`
- `customerAccessesAdminEndpoint_returns403`

### Catalog
- `searchProducts_bySpecJsonb_returnsMatching` — `spec.ram=16GB`
- `searchProducts_paginationRespectsSizeLimit` — `size=1000000` bị ép về 100
- `deleteProduct_softDeletes_andHidesFromPublicList`
- `deleteProduct_alreadyInOrder_doesNotBreakOrderHistory` ← bắt đúng lỗi thiết kế của bản v1
- `getProduct_returnsAvailableQuantity_excludingReserved`

### Cart
- `addItem_existingVariant_incrementsQuantity_notDuplicateRow`
- `updateItem_ofAnotherUser_returns404_not403` ← test IDOR
- `addItem_quantityZero_returns400`

### Checkout / Reservation
- `createSession_reservesStock_reducesAvailableForOthers`
- `createSession_insufficientStock_returns409_withPerVariantDetails`
- `expiredReservation_isReleasedByScheduledJob` — dùng Clock, không sleep
- `placeOrder_afterReservationExpired_returns409`
- `redisDown_reservationStillWorks` — tắt container Redis giữa test, khẳng định vẫn đúng

Test cuối là con át chủ bài: nó **chứng minh** cái bạn nói trong ADR 0004, thay vì chỉ tuyên bố.

### Order
- `placeOrder_snapshotsProductNameAndPrice_notReference` — đổi giá product sau khi đặt, đơn cũ giữ nguyên
- `placeOrder_sameIdempotencyKeyTwice_createsOneOrder_returnsSameResponse`
- `placeOrder_differentPayloadSameKey_returns409`
- `transition_invalidTransition_returns409` — chạy tham số hóa qua **toàn bộ** cặp trạng thái không hợp lệ
- `transition_concurrentByCustomerAndAdmin_oneWins409` — kiểm optimistic lock trên `orders`
- `cancelOrder_afterShipped_customerForbidden`

`transition_invalidTransition_returns409` nên viết dưới dạng `@ParameterizedTest` quét hết ma trận trạng thái — 7 trạng thái = 42 cặp, hợp lệ 9, còn 33 cặp phải bị chặn. Một test bao trọn bảng ở `02-luong-nghiep-vu.md`.

### Payment
- `webhook_invalidSignature_returns401`
- `webhook_amountMismatch_doesNotMarkOrderPaid` ← không bao giờ tin webhook
- `webhook_deliveredTwice_isIdempotent`
- `pay_alreadyPaidOrder_returns409`

### Promotion
- `coupon_belowMinOrderAmount_returns422`
- `coupon_percentType_respectsMaxDiscount` — mã 50% tối đa 500k, đơn 3 triệu → giảm đúng 500k
- `coupon_expired_returns422`

Test `respectsMaxDiscount` là test tính tiền — nhớ assert bằng `BigDecimal` với `compareTo`, **không** dùng `equals` (`100.0` và `100.00` không `equals` nhau nhưng `compareTo` bằng 0). Đây là chi tiết đáng nhắc khi phỏng vấn.

---

## 5. Kiểm tra N+1 trong test

Test hồi quy cho N+1, để nó không lặng lẽ quay lại:

```java
@Test
void listOrders_doesNotTriggerNPlusOne() {
    seedOrders(20, /* items each */ 5);
    var stats = sessionFactory.getStatistics();
    stats.clear();

    orderService.listOrders(userId, PageRequest.of(0, 20));

    assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(3);
}
```

Bật `hibernate.generate_statistics=true` ở profile `test`. Test này bắt được lỗi ngay khi ai đó (kể cả bạn, hai tuần sau) xóa mất `@EntityGraph`. Nhắc tới nó trong phỏng vấn khi được hỏi về N+1 — nói "tôi có test chặn" mạnh hơn nhiều so với "tôi biết dùng JOIN FETCH".

---

## 6. CI

`.github/workflows/ci.yml`: `mvn verify` mỗi push và mỗi PR. Docker có sẵn trên `ubuntu-latest` nên Testcontainers chạy được, không cần cài thêm.

Tách hai job:
- `test` — unit + integration, chạy mọi lúc
- `concurrency-test` — 5 test nặng, tag `@Tag("concurrency")`, chạy trên `main` và PR (chậm hơn nhưng không được bỏ)

Thêm badge CI vào README. Badge xanh là thứ đầu tiên người ta nhìn.

---

## 7. Câu hỏi phỏng vấn về test — chuẩn bị sẵn

| Câu hỏi | Ý chính khi trả lời |
|---|---|
| "Test concurrency của bạn viết thế nào?" | `ExecutorService` + `CountDownLatch` thả đồng loạt; assert ba góc (số đơn / tồn kho / tổng đã bán); **và** kể chuyện commit đỏ trước, xanh sau |
| "Sao không mock database?" | Lỗi cần bắt nằm ở tầng khóa và ràng buộc của chính DB — mock DB thì test luôn pass mà production vẫn oversell |
| "Test có bị flaky không?" | Có, ban đầu do dùng `@Transactional` trên test class làm mọi thread chung một transaction. Bỏ đi và chuyển sang truncate thủ công |
| "Coverage bao nhiêu?" | 75% ở tầng service, cố ý không đếm controller/DTO. Nêu rõ tại sao con số tổng thể vô nghĩa |
| "Test thứ gì phụ thuộc thời gian?" | Inject `Clock`, `Clock.fixed` trong test, không `Thread.sleep` |
