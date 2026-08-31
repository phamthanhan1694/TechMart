# Phạm vi & Kế hoạch — TechMart Backend

> **Chốt cấu hình dự án:** Backend-only (demo qua Swagger + script), 5 tuần full-time (~7h/ngày), người làm đã dựng được CRUD Spring Boot.
> Tài liệu này là **hợp đồng với chính mình**. Khi phân vân "có nên làm cái này không", quay lại đây, không quay lại tài liệu hướng dẫn.

---

## 1. Một câu mô tả dự án

> Backend nền tảng thương mại điện tử thiết bị điện tử (điện thoại/laptop), tập trung vào **tính đúng đắn của tồn kho dưới tải đồng thời**: giữ hàng có hạn dùng khi checkout, chống bán quá tồn, mở bán giới hạn (flash sale) có rate limiting, và thanh toán idempotent.

Câu này đi thẳng vào CV và câu mở đầu khi phỏng vấn. Chú ý nó **không** nói "làm website bán hàng có đăng ký đăng nhập giỏ hàng" — ai cũng có phần đó.

---

## 2. Quyết định phạm vi đã chốt

| Hạng mục | Quyết định | Lý do |
|---|---|---|
| Ngành hàng | Chỉ điện thoại + laptop | Có lý do chính đáng cho spec động, variant giá khác nhau, flash sale |
| Kiến trúc | Monolith **module hóa theo domain** | Microservices không tăng điểm cho fresher, chỉ tăng rủi ro trễ |
| Hạ tầng ngoài DB | **Chỉ Redis** | Redis gánh 4 việc: cache, rate limit, reservation TTL, idempotency. Một hạ tầng, bốn câu chuyện |
| Message queue | **Outbox table + `@TransactionalEventListener` + `@Scheduled`** thay RabbitMQ | Đúng pattern hơn, kể chuyện async được, không tốn container. RabbitMQ chỉ gắn ở tuần 5 nếu dư thời gian |
| Realtime | **Bỏ** | Không phục vụ câu chuyện lõi |
| Frontend | **Không làm** | Dồn 100% cho backend; bù phần "nhìn thấy được" bằng mục 6 |
| Thanh toán | **Mock gateway** trong project (có webhook giả) | Tích hợp VNPay thật tốn thời gian, không thêm chiều sâu kỹ thuật |
| Model dữ liệu | **v2 ngay từ migration đầu** (có `product_variants`, `inventory_reservations`) | Xem mục 4 — đây là rủi ro số một |

---

## 3. Ba câu chuyện kỹ thuật phải kể được (mọi thứ khác là phụ)

1. **Chống oversell dưới tải đồng thời** — chọn khóa gì, vì sao, và *test chứng minh*.
2. **Giữ hàng có hạn dùng (reservation TTL)** — Redis hay DB, chuyện gì xảy ra khi Redis chết.
3. **Flash sale + rate limit** — vì sao `synchronized` vô dụng khi chạy nhiều instance, tại sao trừ tồn bằng Lua script trên Redis.

Nếu tuần 5 mà ba câu này chưa kể trơn tru, **dừng làm tính năng mới**, quay lại làm sâu ba cái này.

---

## 4. Rủi ro số một và cách chặn

Repo hiện có **hai bản thiết kế mâu thuẫn**: bản đặc tả cũ (`dac-ta-ecommerce-backend.md`) để tồn kho ngay trên `products`; bản hướng dẫn để tồn kho trên `product_variants` kèm `inventory_reservations`.

**Nếu code theo bản cũ rồi nâng cấp, phải viết lại toàn bộ cart/order/inventory — mất đứt ~1 tuần.**

- Chặn bằng cách: migration Flyway `V1__init.sql` dựng **thẳng schema v2** theo `01-domain-v2.md`. Tuần 1 mỗi sản phẩm có thể chỉ có 1 variant, nhưng bảng phải có sẵn.
- `dac-ta-ecommerce-backend.md` và `erd-ecommerce.md` từ nay chỉ còn giá trị **tham khảo phần lý thuyết** (BigDecimal, snapshot giá, N+1), không phải bản thiết kế thi hành.

---

## 5. Kế hoạch 5 tuần & Definition of Done

Mỗi tuần chỉ "xong" khi **toàn bộ** dòng DoD đúng. Không mang nợ sang tuần sau — nợ kỹ thuật ở tuần 2 sẽ giết tuần 4.

### Tuần 1 — Nền móng + Catalog

- Skeleton Spring Boot 3 / Java 21, package theo domain: `catalog`, `identity`, `cart`, `order`, `inventory`, `payment`, `promotion`, `common`
- `docker-compose.yml`: app + PostgreSQL + Redis
- Flyway `V1__init.sql` — **toàn bộ schema v2**
- Auth: register / login / refresh / logout. JWT access (15 phút) + refresh token **có rotation và revoke** (lưu hash trong DB)
- RBAC: `CUSTOMER`, `STAFF`, `ADMIN` qua `@PreAuthorize`
- `@RestControllerAdvice` + error envelope chuẩn + `04-error-catalog.md` áp dụng từ ngày đầu
- CRUD Category / Brand / Product / Variant (ADMIN) — **soft delete**, không xóa cứng
- `GET /products` — phân trang, sort, lọc theo category/brand/khoảng giá, tìm theo tên, **lọc theo spec JSONB**
- Seed 300–500 sản phẩm thật (có spec, có ảnh URL thật)
- Springdoc OpenAPI bật, nhóm tag rõ ràng
- GitHub Actions: build + test mỗi push

**DoD:** `docker compose up` là chạy được; Swagger mở ra thấy đủ nhóm Auth + Catalog; gọi `GET /products?keyword=iphone&minPrice=...&spec.ram=8GB` trả đúng; CI xanh; seed có ≥300 sản phẩm không phải "Test Product".

### Tuần 2 — Lõi: Cart → Order → chống oversell

- Cart: thêm/sửa/xóa item, xóa sạch giỏ. Lấy user từ token, **không nhận `userId` từ client**
- Order state machine theo bảng chuyển trạng thái ở `02-luong-nghiep-vu.md`, ghi `order_status_history`
- `POST /orders` — một `@Transactional`: kiểm tồn → trừ tồn → tạo order + items (**snapshot `product_name`, `sku`, `unit_price`**) → dọn giỏ
- Chống oversell: **pessimistic lock làm mặc định** (`@Lock(PESSIMISTIC_WRITE)`), có nhánh optimistic (`@Version` + retry) để so sánh và đo
- Hủy đơn + hoàn kho, **idempotent** (hủy 2 lần không hoàn kho 2 lần)
- Testcontainers (PostgreSQL + Redis) cho integration test
- **Concurrency test**: 200 thread cùng mua variant còn 10 → đúng 10 đơn thành công, tồn kho = 0, không âm

**DoD:** concurrency test pass. Trong lịch sử git có commit cho thấy test này **fail trước khi thêm lock** và pass sau — đây là bằng chứng bạn hiểu vấn đề, giữ lại làm tài sản phỏng vấn.

### Tuần 3 — Reservation + Payment + Promotion

- `inventory_reservations`: tạo khi vào checkout, TTL 10 phút. `available = stock_quantity - reserved_quantity`
- Redis key có TTL làm đường nhanh, **bảng DB là nguồn sự thật**; `@Scheduled` quét reservation quá hạn mỗi 30 giây → fallback khi Redis chết (viết ADR cho quyết định này)
- Mock payment gateway: `POST /payments` + endpoint webhook giả `POST /payments/webhook` để mô phỏng callback thành công/thất bại
- **`Idempotency-Key`** header cho `POST /orders` và `POST /payments`; unique constraint ở DB + cache Redis
- Coupon engine: `PERCENT` / `FIXED`, `min_order_amount`, `max_discount`, `usage_limit`, `per_user_limit`
- **Test race coupon**: 50 user cùng dùng mã còn 1 lượt → đúng 1 người thành công
- `outbox_events` + `@TransactionalEventListener(AFTER_COMMIT)` + worker `@Scheduled` → "gửi email" (ghi log / MailHog)

**DoD:** gọi `POST /orders` hai lần cùng `Idempotency-Key` chỉ tạo 1 đơn và trả cùng response; reservation hết hạn tự nhả cả khi tắt Redis giữa chừng.

### Tuần 4 — Flash sale + Rate limit + Admin + Observability

- `flash_sales` / `flash_sale_items`: khung giờ, số lượng bán, `per_user_limit`
- Trừ tồn flash sale bằng **Redis Lua script** (atomic: kiểm tồn + giới hạn/user + trừ trong một lệnh), đồng bộ về DB qua outbox
- Rate limit Bucket4j + Redis theo user và theo IP; trả `429` kèm `Retry-After`
- Admin: danh sách/lọc đơn, đổi trạng thái, điều chỉnh tồn kho (có ghi log), dashboard doanh thu theo ngày/tháng (query aggregate, không load hết về app)
- Rà **index** theo mục 8 của `01-domain-v2.md`, chạy `EXPLAIN ANALYZE` cho 3 truy vấn nặng nhất
- Actuator + Prometheus + Grafana (thêm vào docker-compose), 1 dashboard: RPS, p95 latency, tỉ lệ lỗi, số reservation đang giữ

**DoD:** bắn 500 request đồng thời vào flash sale 50 suất → đúng 50 đơn, phần còn lại nhận `409 SOLD_OUT` hoặc `429`, service không sập, Grafana thấy được đỉnh traffic.

### Tuần 5 — Đo đạc, đóng gói, kể chuyện

- k6: kịch bản duyệt sản phẩm + kịch bản flash sale. Ghi bảng **p95 / throughput trước và sau** khi bật Redis cache + index
- Viết 6–8 ADR trong `docs/adr/` (xem mục 7)
- README: mô tả bài toán, sơ đồ kiến trúc, ERD, 3 câu chuyện kỹ thuật, bảng số liệu, hướng dẫn 1 lệnh, tài khoản demo, link Swagger deploy
- Deploy Render/Railway + Neon/Supabase (Postgres) + Upstash (Redis)
- Postman collection + `demo.sh` chạy trọn luồng, quay asciinema
- Rà `07-security-checklist.md`
- *(Nếu dư ≥2 ngày)* gắn RabbitMQ thay worker outbox ở đúng một chỗ

**DoD:** người lạ clone về, chạy một lệnh, mở Swagger đặt được đơn. Link deploy sống. README đọc 3 phút là hiểu bạn đã giải bài toán gì.

---

## 6. Backend-only thì lấy gì làm "demo"?

Đây là điểm yếu duy nhất của lựa chọn backend-only, và bù được hết bằng bốn thứ, tổng chi phí khoảng 1 ngày:

1. **`demo.sh` + asciinema GIF** — script chạy trọn luồng (đăng nhập → thêm giỏ → checkout giữ hàng → thanh toán → admin đổi trạng thái), in kết quả có màu. Kèm một script bắn 500 request đồng thời vào flash sale, in ra `50 thành công / 312 sold out / 138 rate-limited`. **Cái GIF này ấn tượng hơn một storefront React tầm thường.**
2. **Grafana dashboard screenshot** ngay lúc đỉnh traffic.
3. **k6 HTML report** commit vào `docs/loadtest/`.
4. **Swagger UI** nhóm tag gọn gàng, mọi endpoint có mô tả và ví dụ request/response.

---

## 7. Danh sách ADR sẽ viết (mỗi cái 15–20 dòng)

| # | Quyết định |
|---|---|
| 0001 | Monolith module hóa thay vì microservices |
| 0002 | JSONB cho product spec thay vì EAV |
| 0003 | Pessimistic lock cho đơn thường, Redis atomic cho flash sale |
| 0004 | Reservation: DB là nguồn sự thật, Redis là đường nhanh |
| 0005 | Outbox pattern thay message broker |
| 0006 | Refresh token có rotation + revoke, lưu hash |
| 0007 | Soft delete cho product/variant |
| 0008 | `Idempotency-Key` ở tầng API thay vì chống trùng ở client |

Mẫu mỗi ADR: **Bối cảnh → Các lựa chọn đã cân nhắc → Quyết định → Đánh đổi chấp nhận → Khi nào nên đổi ý.** Mục cuối cùng là mục ăn điểm nhất, đừng bỏ.

---

## 8. Kế hoạch cắt khi trễ

Trễ là chuyện bình thường. Cắt theo đúng thứ tự này, cắt từ trên xuống:

1. Serial/IMEI + bảo hành
2. Grafana (giữ Actuator + Prometheus)
3. Outbox worker (gọi trực tiếp, ghi TODO + giải thích được tại sao nên tách)
4. Coupon engine
5. Flash sale (giữ rate limit)

**Tuyệt đối không cắt:** chống oversell + concurrency test · reservation TTL · `Idempotency-Key` · README + ADR · `docker compose up` một lệnh · link deploy sống.

Lý do của thứ tự này: mỗi thứ bị cắt ở trên đều **không phá vỡ câu chuyện kỹ thuật nào trong ba câu ở mục 3**.

---

## 9. Nhịp làm việc

- Mỗi ngày: ít nhất 1 commit có nghĩa, theo conventional commits (`feat(order): ...`, `fix(inventory): ...`)
- Mỗi tính năng lõi: 1 nhánh + 1 PR có mô tả, tự merge. Lịch sử PR là thứ nhà tuyển dụng đọc được
- Viết ADR **ngay lúc ra quyết định**, không để dồn cuối dự án — dồn lại sẽ viết ra thứ giả tạo, và interviewer nhận ra
- Cuối mỗi tuần: đọc lại DoD, đánh dấu, và tự trả lời thành tiếng ba câu ở mục 3
