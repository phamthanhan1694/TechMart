# Hướng dẫn Project: E-commerce Đồ Điện Tử (Fullstack)
### Dành cho fresher apply vị trí Backend/Fullstack Java

---

## 1. Tổng quan project

**Tên gợi ý:** TechMart / ElectroShop / GadgetHub (đặt tên tùy bạn)

**Mô tả 1 câu (dùng khi giới thiệu trong CV/phỏng vấn):**
> Nền tảng thương mại điện tử chuyên bán thiết bị điện tử (điện thoại, laptop), có xử lý flash sale giới hạn số lượng, giữ hàng tạm thời khi checkout, và trang quản trị (Admin panel) cho vận hành kho/đơn hàng.

**Phạm vi ngành hàng:** Chỉ nên chọn **1-2 ngành hàng** để đào sâu thay vì bán "đủ thứ":
- Điện thoại (thông số: RAM, ROM, màn hình, camera, pin...)
- Laptop (thông số: CPU, RAM, ổ cứng, GPU, màn hình...)

→ Thu hẹp phạm vi giúp bạn có lý do chính đáng để giải quyết bài toán **thuộc tính sản phẩm động (dynamic spec)** — điểm nhấn kỹ thuật quan trọng.

**Đối tượng sử dụng (2 phía):**
1. **Customer-facing:** khách mua hàng
2. **Admin Panel:** quản lý kho, đơn hàng, sản phẩm, thống kê doanh thu

---

## 2. Tech Stack đề xuất

| Layer | Công nghệ |
|---|---|
| Backend | Java 17+, Spring Boot 3, Spring Security, Spring Data JPA |
| Database | PostgreSQL (khuyến nghị vì hỗ trợ JSONB tốt cho dynamic spec) |
| Cache/Lock | Redis |
| Message Queue | RabbitMQ (hoặc Kafka nếu muốn thể hiện thêm) |
| Auth | JWT (access token + refresh token) |
| API Doc | Springdoc OpenAPI (Swagger UI) |
| Migration | Flyway |
| Testing | JUnit 5, Mockito, Testcontainers |
| Frontend | ReactJS + TypeScript, Redux Toolkit hoặc Zustand, TailwindCSS |
| Realtime | WebSocket (STOMP) hoặc Server-Sent Events |
| Containerize | Docker, Docker Compose |
| CI/CD | GitHub Actions |
| Deploy | Render/Railway (backend + DB), Vercel/Netlify (frontend) |
| Load test (optional) | k6 hoặc JMeter |

---

## 3. Danh sách tính năng (chia theo độ ưu tiên)

### 3.1. Nhóm MUST-HAVE (bắt buộc để có project hoàn chỉnh)

**Auth & User**
- Đăng ký / đăng nhập (JWT + refresh token)
- Phân quyền: `CUSTOMER`, `STAFF`, `ADMIN`
- Quên mật khẩu (gửi email qua queue bất đồng bộ)

**Sản phẩm**
- CRUD sản phẩm (Admin)
- Thuộc tính động theo loại sản phẩm (dynamic spec — xem mục 5)
- Tìm kiếm + lọc theo thông số (giá, RAM, hãng...) + phân trang, sắp xếp

**Giỏ hàng & Đặt hàng**
- Thêm/xóa/sửa giỏ hàng
- Checkout → tạo đơn hàng
- **Giữ hàng tạm thời (hold inventory)** khi vào bước thanh toán, tự nhả sau X phút nếu không thanh toán (điểm nhấn kỹ thuật #1)
- State machine đơn hàng: `PENDING → PAID → PACKED → SHIPPED → DELIVERED` + `CANCELLED`/`RETURNED`

**Tồn kho**
- Trừ kho khi đặt hàng, đảm bảo **không oversell** khi nhiều người mua cùng lúc (điểm nhấn kỹ thuật #2 — concurrency)

**Admin Panel**
- Quản lý sản phẩm, tồn kho
- Quản lý đơn hàng (duyệt, cập nhật trạng thái vận chuyển)
- Dashboard doanh thu cơ bản (biểu đồ theo ngày/tháng)

### 3.2. Nhóm NICE-TO-HAVE (làm để tạo điểm nhấn khi phỏng vấn)

- **Flash Sale**: mở bán giới hạn số lượng theo khung giờ, có rate limiting để chống bot/spam request (điểm nhấn kỹ thuật #3)
- **Review/Rating**: chỉ cho review nếu đã mua hàng (verified purchase)
- **Bảo hành/Đổi trả (Warranty flow)**: tạo yêu cầu đổi trả, admin duyệt
- **Thông báo realtime**: WebSocket báo trạng thái đơn hàng thay đổi
- **Cache sản phẩm hot** bằng Redis (cache-aside pattern)
- **Idempotency key** khi thanh toán (tránh double-charge nếu user bấm 2 lần hoặc mạng lag)
- **Recommendation đơn giản**: "sản phẩm liên quan" dựa theo category/tag

### 3.3. Nhóm KHÔNG NÊN LÀM (tránh ôm đồm, không tăng giá trị phỏng vấn)

- Tích hợp thanh toán thật (VNPay/Momo thật) — dùng **mock payment gateway** là đủ, tập trung effort vào logic hơn là tích hợp bên thứ 3
- Chat trực tiếp với shop (live chat) — không liên quan trọng tâm
- Đa ngôn ngữ (i18n) — không cần thiết ở portfolio fresher

---

## 4. Điểm nhấn kỹ thuật — Nội dung nên "kể chuyện" khi phỏng vấn

### Điểm nhấn #1: Giữ hàng tạm thời (Reservation/Hold Inventory)
**Vấn đề:** User A cho sản phẩm vào giỏ, vào trang thanh toán nhưng chưa bấm "Đặt hàng". Nếu không giữ hàng, sản phẩm có thể bị người khác mua hết trong lúc A đang nhập thông tin thanh toán → A bị lỗi "hết hàng" giữa chừng, trải nghiệm tệ.

**Giải pháp:**
- Khi user vào bước checkout, tạo 1 bản ghi `reservation` (hoặc dùng Redis key có TTL) giữ số lượng trong X phút (VD 10 phút).
- Tồn kho hiển thị cho người khác = `tồn kho thật - tổng đang được giữ`.
- Hết thời gian TTL mà chưa thanh toán → tự động nhả lại (dùng Redis TTL callback, hoặc Scheduled Job quét bảng reservation hết hạn).

**Câu hỏi phỏng vấn có thể gặp:** "Nếu server crash giữa lúc đang giữ hàng thì sao?", "Vì sao chọn TTL bằng Redis thay vì cron job quét DB?"

### Điểm nhấn #2: Chống Oversell (Concurrency Control)
**Vấn đề:** 2 request cùng lúc cùng mua sản phẩm còn 1 cái trong kho → nếu không kiểm soát, cả 2 đều được xác nhận thành công (oversell).

**Giải pháp — chọn 1 trong các cách và giải thích được trade-off:**
- **Pessimistic Locking**: `SELECT ... FOR UPDATE` khi trừ kho — an toàn tuyệt đối nhưng giảm throughput khi tải cao.
- **Optimistic Locking**: dùng cột `version`, nếu update thất bại do version đổi thì retry — throughput tốt hơn nhưng cần xử lý retry logic.
- **Redis atomic (`DECR`)**: dùng cho sản phẩm hot/flash sale, giảm tải cho DB chính.

**Câu hỏi phỏng vấn có thể gặp:** "Khác nhau giữa Optimistic và Pessimistic Locking?", "Tại sao không dùng `synchronized` trong code Java?" (vì không hoạt động đúng khi chạy nhiều instance/server).

### Điểm nhấn #3: Flash Sale + Rate Limiting
**Vấn đề:** Mở bán sản phẩm hot số lượng giới hạn theo khung giờ → traffic dồn cực lớn trong vài giây đầu, cần chống nghẽn hệ thống và bot spam request.

**Giải pháp:**
- Rate limiting theo user/IP (thuật toán Token Bucket hoặc Sliding Window) — dùng thư viện Bucket4j + Redis để scale nhiều instance.
- Đưa order vào **queue** (RabbitMQ) xử lý tuần tự thay vì xử lý đồng bộ trực tiếp vào DB — tránh nghẽn DB khi traffic đỉnh điểm.
- Trả về HTTP 429 kèm header `Retry-After` khi vượt giới hạn.

### Điểm nhấn #4: Dynamic Product Specification
**Vấn đề:** Điện thoại có thuộc tính (RAM, camera...) khác với Laptop (CPU, GPU...) — nếu tạo cột cố định trong bảng `product` sẽ không linh hoạt khi thêm ngành hàng mới.

**Giải pháp — 2 hướng, chọn 1 và giải thích được lý do:**
- **EAV pattern** (Entity-Attribute-Value): bảng `product_attribute` dạng key-value — linh hoạt tối đa nhưng query phức tạp hơn, khó đánh index.
- **JSONB column** (PostgreSQL): lưu spec dạng JSON trong 1 cột, dùng GIN index để query hiệu quả — cân bằng tốt giữa linh hoạt và hiệu năng, khuyến nghị dùng cách này.

### Điểm nhấn #5: Idempotency khi thanh toán
**Vấn đề:** User bấm nút "Thanh toán" 2 lần (do lag mạng, double click) → có thể tạo 2 đơn hàng hoặc trừ tiền 2 lần.

**Giải pháp:** Client gửi kèm `Idempotency-Key` (UUID) mỗi lần request thanh toán; server lưu key đã xử lý (Redis, TTL vài phút), nếu key đã tồn tại thì trả về kết quả cũ thay vì xử lý lại.

---

## 5. Thiết kế Database (ERD tóm tắt)

**Các bảng chính:**

```
users (id, email, password_hash, full_name, role, created_at)

categories (id, name, slug)

products (id, category_id, name, slug, description, base_price, brand, thumbnail_url, created_at)

product_specs (id, product_id, spec_json JSONB)   -- hoặc EAV: product_attributes(product_id, attr_key, attr_value)

product_variants (id, product_id, sku, price, stock_quantity, version)  -- version dùng cho optimistic lock

inventory_reservations (id, variant_id, user_id, quantity, expires_at, status)

carts (id, user_id)
cart_items (id, cart_id, variant_id, quantity)

orders (id, user_id, status, total_amount, idempotency_key, created_at)
order_items (id, order_id, variant_id, quantity, price_at_purchase)

payments (id, order_id, status, method, transaction_ref, paid_at)

reviews (id, product_id, user_id, order_item_id, rating, comment, created_at)

flash_sales (id, product_variant_id, start_time, end_time, sale_quantity, sold_quantity)

warranty_requests (id, order_item_id, status, reason, created_at)
```

**Lưu ý thiết kế:**
- `product_variants` tách riêng vì 1 sản phẩm có thể có nhiều phiên bản (màu, dung lượng) với giá/tồn kho khác nhau.
- `orders.idempotency_key` có unique constraint để chống tạo trùng đơn hàng.
- `product_variants.version` dùng cho Optimistic Locking (`@Version` trong JPA).

---

## 6. Kiến trúc hệ thống (mức tổng quan)

```
[React Frontend] ──REST/WebSocket──> [Spring Boot API Gateway/Monolith]
                                              │
                    ┌─────────────────────────┼─────────────────────────┐
                    │                         │                         │
              [PostgreSQL]              [Redis Cache]           [RabbitMQ Queue]
              (dữ liệu chính)         (session, cache,          (xử lý order async,
                                        rate limit,               gửi email)
                                        reservation TTL)
```

**Lưu ý:** Không cần làm microservices thật sự (quá phức tạp cho fresher) — làm **monolith có module hóa rõ ràng** (package theo domain: `product`, `order`, `inventory`, `payment`...) là đủ, và bạn vẫn có thể giải thích "nếu scale thì sẽ tách service nào trước" khi được hỏi.

---

## 7. Kế hoạch triển khai theo tuần (gợi ý ~8 tuần, làm part-time)

| Tuần | Nội dung |
|---|---|
| 1 | Setup project, thiết kế DB, viết migration (Flyway), Auth (JWT) |
| 2 | CRUD sản phẩm + category, dynamic spec (JSONB), tìm kiếm/lọc/phân trang |
| 3 | Giỏ hàng, checkout cơ bản, order state machine |
| 4 | Giữ hàng tạm thời (reservation + TTL), chống oversell (locking) |
| 5 | Flash sale + rate limiting + queue xử lý order async |
| 6 | Admin panel: quản lý đơn hàng, tồn kho, dashboard doanh thu |
| 7 | Frontend hoàn thiện UI, kết nối WebSocket cho update realtime |
| 8 | Viết test (unit + integration + concurrency test), Docker hóa, deploy, viết README |

---

## 8. Testing Strategy

- **Unit test:** logic tính giá, logic reservation, logic rate limiter (dùng `Clock` injectable, không `Thread.sleep` thật).
- **Integration test:** dùng `MockMvc`/`WebTestClient` test API, dùng **Testcontainers** cho PostgreSQL + Redis thật khi test (không mock DB).
- **Concurrency test:** dùng `ExecutorService` + `CountDownLatch` giả lập nhiều thread cùng mua 1 sản phẩm, assert tổng số đơn thành công ≤ tồn kho.
- **Load test (optional, ăn điểm thêm):** dùng k6 bắn traffic vào endpoint flash sale, kiểm tra hệ thống trả đúng % request 429 và không crash.

---

## 9. README nên có gì (rất quan trọng — interviewer đọc README trước khi hỏi)

1. Mô tả bài toán + đối tượng người dùng
2. Kiến trúc hệ thống (kèm sơ đồ)
3. ERD
4. Danh sách API chính (hoặc link Swagger)
5. Các quyết định kỹ thuật quan trọng + lý do (VD: "vì sao chọn Optimistic Locking cho X nhưng Redis atomic cho Y")
6. Hướng dẫn chạy local (Docker Compose)
7. Link demo (deploy) + tài khoản test
8. Ảnh chụp màn hình hoặc GIF demo tính năng chính (đặc biệt là flash sale/reservation)

---

## 10. Câu hỏi phỏng vấn nên tự luyện trả lời trước

- Tại sao chọn Optimistic Locking / Pessimistic Locking / Redis atomic cho từng trường hợp cụ thể trong project?
- Nếu 2 instance backend cùng chạy (scale ngang), rate limiter/reservation của bạn còn đúng không? Vì sao?
- Nếu Redis chết giữa chừng thì hệ thống reservation xử lý thế nào (fallback)?
- Bạn xử lý N+1 query ở những chỗ nào trong project (VD: load order kèm order_items)?
- Test concurrency bạn viết như thế nào, chứng minh ra sao là đúng?
- Nếu traffic tăng 100 lần vào giờ flash sale, hệ thống nghẽn ở đâu đầu tiên? Bạn sẽ scale phần nào trước?
- JSONB cho product spec có nhược điểm gì so với thiết kế bảng cố định?

---

## Ghi chú cuối

Không cần làm hết toàn bộ mục 3.2 (Nice-to-have) — chọn **2-3 điểm nhấn kỹ thuật làm thật sâu, có test, có thể giải thích rành mạch** sẽ có giá trị hơn nhiều so với làm đủ hết tính năng nhưng hời hợt. Ưu tiên độ sâu hơn độ rộng.
