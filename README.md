# TechMart — E-commerce Backend

> Backend nền tảng thương mại điện tử thiết bị điện tử (điện thoại / laptop), tập trung vào **tính đúng đắn của tồn kho dưới tải đồng thời**: giữ hàng có hạn dùng khi checkout, chống bán quá tồn, mở bán giới hạn có rate limiting, và thanh toán idempotent.

`Java 21` · `Spring Boot 3.5` · `PostgreSQL 16` · `Redis 7` · `Flyway` · `Testcontainers` · `Prometheus + Grafana` · `k6` · `Docker`

---

## Bài toán

Hàng điện tử giá trị cao, tồn kho thấp, nhiều người tranh mua cùng lúc. Bốn vấn đề mà một CRUD giỏ hàng thông thường không giải được:

1. **Bán quá tồn** — hai request cùng đọc tồn kho = 1, cả hai cùng thấy đủ hàng, cả hai cùng trừ.
2. **Mất hàng giữa lúc thanh toán** — khách đang nhập địa chỉ thì hàng bị người khác mua mất.
3. **Mở bán giới hạn** — vài nghìn request/giây đổ vào *cùng một dòng* dữ liệu trong vài giây đầu.
4. **Đặt trùng đơn / trả tiền hai lần** — khách double-click, hoặc cổng thanh toán gửi lại callback.

---

## Ba điểm nhấn kỹ thuật

### 1. Chống bán quá tồn — có test chứng minh

Variant được **khóa bi quan theo thứ tự `id`** trước khi trừ tồn. `ORDER BY id` là chi tiết quan trọng: đơn A mua `[variant 5, variant 9]` và đơn B mua `[variant 9, variant 5]`, nếu khóa theo thứ tự client gửi thì A giữ 5 chờ 9 còn B giữ 9 chờ 5 → deadlock.

DB có chốt chặn cuối cùng: `CHECK (stock_quantity >= 0)` và `CHECK (reserved_quantity <= stock_quantity)`. Nếu logic khóa ở tầng trên hở, DB ném lỗi chứ không để tồn kho âm.

**Kết quả đo** ([`PlaceOrderConcurrencyIT`](src/test/java/dev/techmart/order/PlaceOrderConcurrencyIT.java)) — 200 thread cùng mua sản phẩm còn 10 cái:

```
Thành công=10  Bị từ chối=190 (INSUFFICIENT_STOCK)
tồn kho còn=0  đang giữ=0  số đơn trong DB=10
```

> **Lần chạy đầu tiên test này fail** — 1 thành công / 199 lỗi `ObjectOptimisticLockingFailureException`. Nguyên nhân: luồng giữ hàng nạp variant vào persistence context bằng `JOIN FETCH` **trước** khi chạy query `FOR UPDATE`. Khi entity đã được quản lý với version cũ mà query có lock mode trả về dòng có version mới hơn, Hibernate ném `StaleObjectStateException` ngay tại câu query khóa — khóa đúng dòng nhưng không bán được hàng, và `em.refresh()` sau đó không cứu được vì ngoại lệ đã ném ra rồi. Thứ phải sửa là **thứ tự nạp**. Xem [`VariantLocker`](src/main/java/dev/techmart/inventory/service/VariantLocker.java) và commit `3d53069`.

### 2. Giữ hàng có hạn dùng — và chuyện Redis chết

Vào bước checkout, hệ thống giữ hàng 10 phút: `available = stock − reserved`. Hết hạn mà chưa đặt hàng thì job `@Scheduled` nhả ra.

Quyết định thiết kế: **DB là nguồn sự thật, Redis chỉ là đường nhanh** ([ADR-0004](docs/adr/0004-reservation-nguon-su-that.md)). Redis keyspace notification là *at-most-once* — mất một event là hàng bị giữ vĩnh viễn, và Redis restart là mất sạch reservation. Job quét DB mỗi 30 giây chậm hơn nhưng không bao giờ mất. Hàng nhả muộn 30 giây không hại ai; hàng kẹt vĩnh viễn thì có.

### 3. Flash sale — Redis là *cửa*, DB vẫn là *sổ cái*

Mở bán giới hạn có mô hình tải khác hẳn phần còn lại: vài nghìn request/giây vào **cùng một dòng**. Khóa bi quan ở DB không chịu nổi — mọi request xếp hàng chờ cùng một row lock, connection pool cạn, và các API khác chết theo.

Kiến trúc hai tầng:

- **Cửa Redis** — [Lua script](src/main/resources/redis/flashsale-purchase.lua) kiểm tồn + kiểm giới hạn/user + trừ trong **một** lệnh nguyên tử. Phải là Lua chứ không phải `DECR` rồi kiểm tra: `DECR` → thấy âm → `INCR` bù là *ba* lệnh không nguyên tử, có khe hở cho request khác đọc phải tồn âm.
- **Sổ cái DB** — chỉ người **đã thắng** ở cửa mới mở transaction ghi đơn. Số transaction chạm vào dòng dữ liệu bị chặn trên bởi `sale_quantity` — vài chục, không phải vài nghìn.
- Ghi DB thất bại thì **trả lại suất** cho Redis, nếu không suất đó bị bốc hơi.

Rate limit dùng [token bucket viết bằng Lua](src/main/resources/redis/rate-limit.lua), hai tầng theo **user và theo IP** — một user đổi được IP, còn một IP sau NAT có nhiều user thật.

**Kết quả đo** ([`FlashSaleConcurrencyIT`](src/test/java/dev/techmart/flashsale/FlashSaleConcurrencyIT.java)) — 500 người tranh 50 suất:

```
Bán được=50  Hết suất=450  Lỗi khác=0
sold_quantity(DB)=50   tồn Redis=0   số đơn=50
```

> **Redis chết thì rate limit fail-open, flash sale fail-closed.** Cùng một hạ tầng hỏng, hai chức năng ứng xử ngược nhau — vì hậu quả của việc sai khác nhau: chặn nhầm người dùng thật chỉ gây phiền, còn bán quá số suất thì phải đền bù thật.

---

## Kết quả kiểm chứng

Đo trên máy local (Windows 11, JDK 26, Docker Desktop, PostgreSQL 16 + Redis 7 trong container, 1 instance app).

| Hạng mục | Kết quả |
|---|---|
| 200 thread mua sản phẩm còn 10 | đúng 10 đơn, tồn kho về 0, không âm, không hàng giữ treo |
| 500 người tranh 50 suất flash sale | đúng 50 đơn, `sold_quantity` DB khớp tuyệt đối, Redis về 0, **0 lỗi 5xx** |
| 50 người tranh coupon còn 1 lượt | đúng 1 người được giảm giá, `used_count = 1` |
| Hủy đơn hai lần đồng thời | hoàn kho đúng một lần |
| Đặt hàng / thanh toán hai lần cùng `Idempotency-Key` | một bản ghi duy nhất, trả cùng response |
| Webhook sai chữ ký / sai số tiền | bị từ chối, đơn **không** chuyển sang PAID |
| Rate limit `/auth/login` (5/phút/IP) | 4×`401` rồi `429` kèm `Retry-After` và `X-RateLimit-*` |
| Seed dữ liệu demo | 345 sản phẩm / 850 variant / 504 user / 400 đơn / 3 phiên flash sale trong **11 giây** |
| `mvn verify` | **17 test**, xanh toàn bộ (7 trong đó là test đồng thời trên PostgreSQL thật) |

> Bảng p95 trước/sau khi thêm index và cache **chưa đo** — script k6 đã sẵn sàng ở [loadtest/](loadtest/), quy trình đo ở [docs/06-nfr-va-load-test.md](docs/06-nfr-va-load-test.md).

---

## Chạy thử

```bash
cp .env.example .env
docker compose up -d postgres redis
SPRING_PROFILES_ACTIVE=demo APP_SEED_ENABLED=true mvn spring-boot:run
```

Trọn bộ trong Docker: `docker compose up`
Kèm Prometheus + Grafana: `docker compose --profile observability up -d`

| Giao diện | Địa chỉ |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 (admin/admin) |

### Tài khoản demo

| Vai trò | Email | Mật khẩu | Ghi chú |
|---|---|---|---|
| ADMIN | `admin@techmart.dev` | `Admin@123` | Toàn quyền, xem được dashboard |
| STAFF | `staff@techmart.dev` | `Staff@123` | Xử lý đơn, không xem được doanh thu |
| CUSTOMER | `customer@techmart.dev` | `Customer@123` | **Có sẵn ~17 đơn lịch sử** |
| CUSTOMER | `new@techmart.dev` | `Customer@123` | Tài khoản trống, để demo luồng từ đầu |

### Thử luồng chính bằng Swagger

1. `POST /api/v1/auth/login` → copy `accessToken` → bấm **Authorize**
2. `GET /api/v1/products?keyword=iphone&spec.ram=8GB&sort=price,asc` — lọc theo thông số kỹ thuật trên cột JSONB
3. `POST /api/v1/cart/items` — thêm vào giỏ (**không** giữ hàng)
4. `POST /api/v1/checkout/sessions` — **giữ hàng 10 phút**; `availableQuantity` giảm ngay nhưng `stockQuantity` chưa đổi
5. `POST /api/v1/orders` kèm `Idempotency-Key` — gọi hai lần cùng key chỉ tạo một đơn
6. `POST /api/v1/payments` → `POST /payments/{id}/simulate-callback` lấy body đã ký → `POST /payments/webhook`
7. `GET /api/v1/flash-sales/active` → `POST /flash-sales/items/{id}/purchase` — mua quá `perUserLimit` sẽ nhận `409`
8. `POST /api/v1/orders/{code}/cancel` — gọi hai lần chỉ hoàn kho một lần

---

## Kiến trúc

Monolith **module hóa theo domain**, không phải microservices — quy mô này chưa cần, và tách service sớm chỉ thêm rủi ro.

```
dev.techmart
├── common      envelope lỗi, phân trang, outbox, rate limit, metrics, cấu hình
├── identity    user, JWT, refresh token rotation, RBAC
├── catalog     brand, category, product, variant, tìm kiếm JSONB
├── cart        giỏ hàng
├── inventory   giữ hàng (reservation), khóa tồn kho, job nhả hàng
├── order       đặt hàng, state machine, hủy đơn + hoàn kho
├── payment     cổng thanh toán mock, webhook ký HMAC, idempotency
├── promotion   coupon engine, khóa lượt dùng
├── flashsale   mở bán giới hạn, bộ đếm Redis Lua
├── admin       quản lý đơn, dashboard doanh thu
└── seed        dữ liệu demo
```

Ngoài PostgreSQL chỉ thêm **một** hạ tầng là Redis — nó gánh reservation fast-path, rate limit, bộ đếm flash sale và idempotency. Một hạ tầng, bốn công dụng.

Xử lý bất đồng bộ dùng **outbox pattern** thay message broker: event ghi trong cùng transaction nghiệp vụ, worker gửi sau khi đã commit. `FOR UPDATE SKIP LOCKED` cho phép chạy nhiều instance worker song song.

- ERD và đặc tả domain: [docs/01-domain-v2.md](docs/01-domain-v2.md)
- Sequence diagram các luồng: [docs/02-luong-nghiep-vu.md](docs/02-luong-nghiep-vu.md)
- Danh mục ~45 mã lỗi: [docs/04-error-catalog.md](docs/04-error-catalog.md)
- Toàn bộ ADR: [docs/adr/](docs/adr/)

---

## Observability

Ngoài metric hạ tầng mặc định (JVM, HikariCP, HTTP), hệ thống phát **metric nghiệp vụ**:

| Metric | Trả lời câu hỏi |
|---|---|
| `techmart_inventory_reservations_active` | Có bao nhiêu hàng đang bị giữ? Tăng mãi không giảm = job nhả hàng hỏng |
| `techmart_outbox_events_pending` | Outbox có bị ứ không? Ứ = worker chết |
| `techmart_inventory_insufficient_stock_total` | Bao nhiêu request bị từ chối vì hết hàng |
| `techmart_flashsale_sold_out_total` | Bao nhiêu request flash sale bị từ chối vì hết suất |
| `techmart_ratelimit_rejected_total` | Bao nhiêu request bị rate limit chặn |

Ba metric cuối là các con số cho biết hệ thống **đang bảo vệ tồn kho**, không phải đang lỗi — khác hẳn ý nghĩa của tỉ lệ 5xx. Dashboard Grafana ([techmart.json](observability/grafana/dashboards/techmart.json)) có 8 panel gồm cả `hikaricp_connections_pending`, vì số connection đang chờ là dấu hiệu sớm nhất của transaction bị giữ quá lâu.

---

## Vài quyết định đáng nói

**Không dùng Lombok.** Máy build chạy JDK 26; annotation processor của Lombok hay vỡ trên JDK mới. Viết tay getter/constructor đổi lại build không phụ thuộc vào một thư viện phải chạy đua với từng bản JDK.

**Tìm kiếm bằng native SQL, không phải Criteria API.** Bộ lọc chạy trên cột JSONB (`spec @> '{"ram":"16GB"}'`) để dùng được GIN index; Criteria API không diễn đạt được toán tử `@>` mà không đăng ký hàm tùy biến. Đổi lại phải **whitelist tên cột sắp xếp** — ghép thẳng chuỗi từ client vào `ORDER BY` là lỗ hổng injection.

**Rate limit tự viết bằng Lua thay vì Bucket4j.** Flash sale đã cần đúng cơ chế Redis + Lua này rồi — một khái niệm cho cả hai tính năng, và không thêm một phụ thuộc phải quản lý vòng đời. Bucket4j vẫn là lựa chọn hợp lý nếu không có sẵn cơ chế đó.

**Dashboard nhóm theo `placed_at`, không phải `created_at`.** `created_at` là cột kỹ thuật do `@CreationTimestamp` ghi lúc chèn dòng; `placed_at` mới là thời điểm nghiệp vụ. Dùng nhầm thì dữ liệu nhập từ hệ thống cũ sẽ dồn hết vào ngày import — triệu chứng đã gặp thật: 400 đơn rải 90 ngày mà biểu đồ chỉ có một cột.

**`@Transactional` phải nằm trên chính method `@Scheduled`, và buộc phải tách bean khi cần cả trong lẫn ngoài transaction.** Spring AOP hoạt động qua proxy nên lời gọi nội bộ không đi qua proxy. Bẫy này xuất hiện hai lần: job nhả hàng ném `TransactionRequiredException` mỗi 30 giây, và flash sale cần gọi Redis *ngoài* transaction rồi ghi DB *trong* transaction — chỉ thỏa mãn được khi tách thành [`FlashSaleOrderWriter`](src/main/java/dev/techmart/flashsale/service/FlashSaleOrderWriter.java).

**Xóa mềm cho product/variant.** Xóa cứng sẽ vi phạm khóa ngoại với `order_items` của đơn cũ. Đơn cũ vẫn đọc được vì `order_items` đã snapshot tên + SKU + giá tại lúc mua.

---

## Chưa làm — có chủ đích

| Hạng mục | Ghi chú |
|---|---|
| Đo p95 trước/sau tối ưu | Script k6 đã sẵn ở [loadtest/](loadtest/); cần chạy và điền bảng |
| Deploy | Render/Railway + Neon + Upstash |
| Review, serial/IMEI, bảo hành | Schema đã có sẵn bảng, chưa làm API |
| Frontend | Dự án tập trung vào backend; Swagger là giao diện sản phẩm |

Và những thứ **cố ý không làm**: tích hợp cổng thanh toán thật (mock đã đủ để thể hiện luồng idempotency + webhook ký HMAC, tích hợp thật chỉ tốn thời gian thủ tục), microservices (nếu tách thì tách `inventory` trước, vì nó có ràng buộc đồng thời riêng biệt), thông báo realtime.
