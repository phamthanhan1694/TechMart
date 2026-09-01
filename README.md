# TechMart — E-commerce Backend

> Backend nền tảng thương mại điện tử thiết bị điện tử (điện thoại / laptop), tập trung vào **tính đúng đắn của tồn kho dưới tải đồng thời**: giữ hàng có hạn dùng khi checkout, chống bán quá tồn, và đặt hàng idempotent.

`Java 21` · `Spring Boot 3.5` · `PostgreSQL 16` · `Redis 7` · `Flyway` · `Testcontainers` · `Docker`

---

## Bài toán

Hàng điện tử giá trị cao, tồn kho thấp, nhiều người tranh mua cùng lúc. Ba vấn đề mà một CRUD giỏ hàng thông thường không giải được:

1. **Bán quá tồn** — hai request cùng đọc tồn kho = 1, cả hai cùng thấy đủ hàng, cả hai cùng trừ.
2. **Mất hàng giữa lúc thanh toán** — khách đang nhập địa chỉ thì hàng bị người khác mua mất.
3. **Đặt trùng đơn** — khách double-click hoặc mạng lag, tạo hai đơn cho một lần mua.

---

## Ba điểm nhấn kỹ thuật

### 1. Chống bán quá tồn — có test chứng minh

Variant được **khóa bi quan theo thứ tự `id`** trước khi trừ tồn. `ORDER BY id` là chi tiết quan trọng: đơn A mua `[variant 5, variant 9]` và đơn B mua `[variant 9, variant 5]`, nếu khóa theo thứ tự client gửi thì A giữ 5 chờ 9 còn B giữ 9 chờ 5 → deadlock.

DB có chốt chặn cuối cùng: `CHECK (stock_quantity >= 0)` và `CHECK (reserved_quantity <= stock_quantity)`. Nếu logic khóa ở tầng trên hở, DB ném lỗi chứ không để tồn kho âm.

**Kết quả đo được** ([`PlaceOrderConcurrencyIT`](src/test/java/dev/techmart/order/PlaceOrderConcurrencyIT.java)) — 200 thread cùng mua sản phẩm còn 10 cái:

```
Thành công=10  Bị từ chối=190 (INSUFFICIENT_STOCK)
tồn kho còn=0  đang giữ=0  số đơn trong DB=10
```

> **Lần chạy đầu tiên test này fail** — 1 thành công / 199 lỗi `ObjectOptimisticLockingFailureException`. Nguyên nhân: luồng giữ hàng nạp variant vào persistence context bằng `JOIN FETCH` **trước** khi chạy query `FOR UPDATE`. Khi entity đã được quản lý với version cũ mà query có lock mode trả về dòng có version mới hơn, Hibernate ném `StaleObjectStateException` ngay tại câu query khóa — khóa đúng dòng nhưng không bán được hàng, và `em.refresh()` sau đó không cứu được vì ngoại lệ đã ném ra rồi. Xem [`VariantLocker`](src/main/java/dev/techmart/inventory/service/VariantLocker.java) và commit `3d53069`.

### 2. Giữ hàng có hạn dùng — và chuyện Redis chết

Vào bước checkout, hệ thống giữ hàng 10 phút: `available = stock − reserved`. Hết hạn mà chưa đặt hàng thì job `@Scheduled` nhả ra.

Quyết định thiết kế: **DB là nguồn sự thật, Redis chỉ là đường nhanh** ([ADR-0004](docs/adr/0004-reservation-nguon-su-that.md)). Redis keyspace notification là *at-most-once* — mất một event là hàng bị giữ vĩnh viễn, và Redis restart là mất sạch reservation. Job quét DB mỗi 30 giây chậm hơn nhưng không bao giờ mất. Hàng nhả muộn 30 giây không hại ai; hàng kẹt vĩnh viễn thì có.

### 3. Đặt hàng idempotent

`POST /orders` bắt buộc header `Idempotency-Key`. Đường nhanh là tra cứu theo key; **chốt chặn thật sự là ràng buộc `UNIQUE(orders.idempotency_key)` ở DB** — hai request song song cùng key thì cái thua vi phạm ràng buộc và đọc lại kết quả của cái thắng.

Cùng triết lý ở mọi nơi: hạ tầng nhanh (Redis) có thể mất, nguồn sự thật (PostgreSQL) thì không.

---

## Kết quả kiểm chứng

Đo trên máy local (Windows 11, Docker Desktop, PostgreSQL 16 + Redis 7 trong container, 1 instance app).

| Hạng mục | Kết quả |
|---|---|
| Concurrency test 200 thread / tồn kho 10 | đúng 10 đơn thành công, tồn kho về 0, không âm |
| Hủy đơn hai lần đồng thời | hoàn kho đúng một lần |
| Đặt hàng hai lần cùng `Idempotency-Key` | một đơn duy nhất, trả cùng response |
| Seed dữ liệu demo | 345 sản phẩm / 850 variant / 504 user / 400 đơn trong **15,2 giây** |
| `mvn verify` | 6 test, xanh toàn bộ (gồm 2 test đồng thời chạy trên PostgreSQL thật) |

> Load test k6 và bảng p95 trước/sau khi thêm cache **chưa làm** — xem mục "Chưa làm".

---

## Chạy thử

```bash
cp .env.example .env
docker compose up -d postgres redis
SPRING_PROFILES_ACTIVE=demo APP_SEED_ENABLED=true mvn spring-boot:run
```

Hoặc chạy trọn bộ trong Docker: `docker compose up`

Mở **http://localhost:8080/swagger-ui.html**

### Tài khoản demo

| Vai trò | Email | Mật khẩu | Ghi chú |
|---|---|---|---|
| ADMIN | `admin@techmart.dev` | `Admin@123` | Toàn quyền |
| STAFF | `staff@techmart.dev` | `Staff@123` | Xử lý đơn |
| CUSTOMER | `customer@techmart.dev` | `Customer@123` | **Có sẵn 17 đơn lịch sử** |
| CUSTOMER | `new@techmart.dev` | `Customer@123` | Tài khoản trống, để demo luồng từ đầu |

### Thử luồng chính bằng Swagger

1. `POST /api/v1/auth/login` → copy `accessToken` → bấm **Authorize**
2. `GET /api/v1/products?keyword=iphone&spec.ram=8GB&sort=price,asc` — lọc theo thông số kỹ thuật trên cột JSONB
3. `POST /api/v1/cart/items` — thêm vào giỏ (**không** giữ hàng)
4. `POST /api/v1/checkout/sessions` — **giữ hàng 10 phút**; `availableQuantity` giảm ngay nhưng `stockQuantity` chưa đổi
5. `POST /api/v1/orders` kèm header `Idempotency-Key` — gọi hai lần cùng key chỉ tạo một đơn
6. `POST /api/v1/orders/{code}/cancel` — gọi hai lần chỉ hoàn kho một lần

---

## Kiến trúc

Monolith **module hóa theo domain**, không phải microservices — quy mô này chưa cần, và tách service sớm chỉ thêm rủi ro ([ADR-0001](docs/adr/README.md)).

```
dev.techmart
├── common      envelope lỗi, phân trang, outbox, cấu hình
├── identity    user, JWT, refresh token rotation, RBAC
├── catalog     brand, category, product, variant, tìm kiếm JSONB
├── cart        giỏ hàng
├── inventory   giữ hàng (reservation), khóa tồn kho, job nhả hàng
├── order       đặt hàng, state machine, hủy đơn + hoàn kho
└── seed        dữ liệu demo
```

Ngoài PostgreSQL chỉ thêm **một** hạ tầng là Redis — nó gánh cache, rate limit, reservation fast-path và idempotency. Một hạ tầng, bốn công dụng.

Xử lý bất đồng bộ dùng **outbox pattern** thay message broker: event ghi trong cùng transaction nghiệp vụ, worker gửi sau khi đã commit. `FOR UPDATE SKIP LOCKED` cho phép chạy nhiều instance worker song song.

- ERD và đặc tả domain: [docs/01-domain-v2.md](docs/01-domain-v2.md)
- Sequence diagram các luồng: [docs/02-luong-nghiep-vu.md](docs/02-luong-nghiep-vu.md)
- Danh mục mã lỗi: [docs/04-error-catalog.md](docs/04-error-catalog.md)

---

## Vài quyết định đáng nói

**Không dùng Lombok.** Máy build chạy JDK 26; annotation processor của Lombok hay vỡ trên JDK mới. Viết tay getter/constructor đổi lại build không phụ thuộc vào một thư viện phải chạy đua với từng bản JDK.

**Tìm kiếm bằng native SQL, không phải Criteria API.** Bộ lọc chạy trên cột JSONB (`spec @> '{"ram":"16GB"}'`) để dùng được GIN index; Criteria API không diễn đạt được toán tử `@>` mà không đăng ký hàm tùy biến. Đổi lại phải **whitelist tên cột sắp xếp** — ghép thẳng chuỗi từ client vào `ORDER BY` là lỗ hổng injection.

**Xóa mềm cho product/variant.** Xóa cứng sẽ vi phạm khóa ngoại với `order_items` của đơn cũ. Đơn cũ vẫn đọc được vì `order_items` đã snapshot tên + SKU + giá tại lúc mua.

**`@Transactional` phải nằm trên chính method `@Scheduled`.** Ban đầu method `@Scheduled` gọi sang method `@Transactional` trong cùng class — Spring AOP hoạt động qua proxy nên lời gọi nội bộ không đi qua proxy, và job ném `TransactionRequiredException` mỗi 30 giây.

Toàn bộ ADR: [docs/adr/](docs/adr/)

---

## Chưa làm — có chủ đích và có kế hoạch

Phần đã xong là **lõi**: auth, catalog, giỏ hàng, giữ hàng, đặt hàng, hủy đơn, outbox. Phần còn lại theo kế hoạch ở [docs/00-pham-vi-va-ke-hoach.md](docs/00-pham-vi-va-ke-hoach.md):

| Hạng mục | Trạng thái |
|---|---|
| Mock payment gateway + webhook ký HMAC | chưa làm (tuần 3) |
| Coupon engine + test race giới hạn lượt dùng | chưa làm (tuần 3) |
| Flash sale (Redis Lua atomic) + rate limit Bucket4j | chưa làm (tuần 4) |
| Admin: quản lý đơn, dashboard doanh thu | chưa làm (tuần 4) |
| Observability: Prometheus + Grafana | chưa làm (tuần 4) |
| Load test k6 + bảng p95 trước/sau | chưa làm (tuần 5) |
| Deploy | chưa làm (tuần 5) |

Và những thứ **cố ý không làm**: tích hợp cổng thanh toán thật (mock đủ để thể hiện luồng idempotency), microservices (nếu tách thì tách `inventory` trước, vì nó có ràng buộc đồng thời riêng biệt), frontend (dự án tập trung vào backend), thông báo realtime.
