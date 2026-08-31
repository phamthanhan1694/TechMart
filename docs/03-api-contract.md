# API Contract v1

> Bổ sung cho `dac-ta-ecommerce-backend.md` phần B — tài liệu cũ mới có CRUD cơ bản, thiếu toàn bộ nhóm checkout, flash sale, payment, coupon, admin.
> Tiền tố: `/api/v1`. Cột **Quyền**: `Public` = không cần token.

---

## 1. Quy ước chung (áp dụng cho mọi endpoint)

### Versioning
Tất cả dưới `/api/v1`. Lý do đưa version vào ngay từ đầu: đổi sau rất đắt, và interviewer sẽ hỏi "nếu phải đổi breaking change thì làm sao".

### Envelope lỗi
Mọi lỗi trả về **đúng một** cấu trúc, sinh từ `@RestControllerAdvice`:

```json
{
  "timestamp": "2026-09-01T10:23:45Z",
  "status": 409,
  "code": "INSUFFICIENT_STOCK",
  "message": "Sản phẩm không đủ số lượng",
  "path": "/api/v1/orders",
  "traceId": "a1b2c3d4",
  "details": [
    { "variantId": 42, "sku": "IP15PM-256-BLK", "requested": 3, "available": 1 }
  ]
}
```

- `code` là mã máy đọc được, tra ở `04-error-catalog.md`. Client hiển thị theo `code`, không parse `message`.
- `details` chỉ có ở lỗi validation và lỗi tồn kho.
- `traceId` lấy từ MDC, đối chiếu được với log. Chi tiết nhỏ nhưng rất "đi làm".
- **Không bao giờ trả stack trace.** Lỗi 500 trả `code: INTERNAL_ERROR` và `traceId`, phần còn lại nằm trong log.

### Phân trang
Response phân trang dùng cấu trúc gọn, **không trả thẳng `Page` của Spring** (nó lộ nhiều field thừa và khó giữ ổn định giữa các version):

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 137,
  "totalPages": 7,
  "hasNext": true
}
```

Giới hạn `size` tối đa 100 — nếu không, một request `size=1000000` là đủ để hạ service.

### Idempotency
`POST /orders`, `POST /payments`, `POST /flash-sales/{id}/purchase` **bắt buộc** header `Idempotency-Key: <uuid>`. Thiếu → `400 IDEMPOTENCY_KEY_REQUIRED`. Gửi lại cùng key → trả response cũ, không tạo mới.

### Xác thực
`Authorization: Bearer <accessToken>`. Access token sống 15 phút, refresh token 7 ngày và **xoay vòng mỗi lần dùng** (dùng lại refresh token cũ → thu hồi cả chuỗi, đây là cách phát hiện token bị đánh cắp).

### Rate limit
Trả header `X-RateLimit-Limit`, `X-RateLimit-Remaining`, và `Retry-After` khi bị chặn (`429`).

---

## 2. Auth — `/api/v1/auth`

| Method | Endpoint | Quyền | Request | Response |
|---|---|---|---|---|
| POST | `/register` | Public | `{email, password, fullName, phone}` | `201` `{id, email, fullName}` |
| POST | `/login` | Public | `{email, password}` | `200` `{accessToken, refreshToken, expiresIn}` |
| POST | `/refresh` | Public | `{refreshToken}` | `200` cặp token mới (token cũ bị thu hồi) |
| POST | `/logout` | Đã đăng nhập | `{refreshToken}` | `204` |
| GET | `/me` | Đã đăng nhập | — | `200` thông tin user hiện tại |

Rate limit `/login`: 5 lần/phút/IP — chống dò mật khẩu. Đăng nhập sai **không** phân biệt "email không tồn tại" với "sai mật khẩu"; cả hai trả `401 INVALID_CREDENTIALS` (chống dò danh sách email).

---

## 3. Catalog — `/api/v1/products`, `/categories`, `/brands`

| Method | Endpoint | Quyền | Ghi chú |
|---|---|---|---|
| GET | `/products` | Public | Phân trang + lọc, xem query params dưới |
| GET | `/products/{slug}` | Public | Trả kèm danh sách variant và `availableQuantity` từng variant |
| GET | `/products/compare?ids=1,2,3` | Public | So sánh spec 2–3 sản phẩm — dùng đúng thế mạnh JSONB, demo đẹp, rẻ |
| POST | `/products` | ADMIN | Tạo product + variant đầu tiên trong một request |
| PUT | `/products/{id}` | ADMIN | |
| DELETE | `/products/{id}` | ADMIN | **Soft delete** — set `deleted_at`, trả `204` |
| POST | `/products/{id}/variants` | ADMIN | |
| PUT | `/variants/{id}` | ADMIN | |
| PATCH | `/variants/{id}/stock` | ADMIN / STAFF | `{delta, reason}` — điều chỉnh tồn, ghi log ai sửa |
| GET/POST/PUT/DELETE | `/categories`, `/brands` | GET Public, còn lại ADMIN | |

**Query params `GET /products`:**

| Param | Ví dụ | Ghi chú |
|---|---|---|
| `page`, `size`, `sort` | `sort=price,asc` | `size` ≤ 100 |
| `keyword` | `keyword=iphone 15` | Trigram index trên `name` |
| `categoryId`, `brandId` | | |
| `minPrice`, `maxPrice` | | Lọc trên `MIN(variant.price)` |
| `spec.<key>` | `spec.ram=16GB&spec.cpu=M3` | Dịch sang `spec @> '{"ram":"16GB"}'`, dùng GIN index |
| `inStock` | `inStock=true` | `stock_quantity - reserved_quantity > 0` |

> **Cache:** `GET /products` và `GET /products/{slug}` cache-aside qua Redis, TTL 60 giây, khóa cache gồm toàn bộ query params đã chuẩn hóa. Xóa cache theo `productId` khi admin sửa. Đây là chỗ để lấy số liệu p95 trước/sau cho README.

---

## 4. Cart — `/api/v1/cart` (CUSTOMER)

| Method | Endpoint | Request | Response |
|---|---|---|---|
| GET | `/cart` | — | `200` giỏ + `subtotal` + cờ `availableQuantity` từng dòng |
| POST | `/cart/items` | `{variantId, quantity}` | `200` giỏ đã cập nhật (đã có variant thì cộng dồn) |
| PUT | `/cart/items/{itemId}` | `{quantity}` | `200` |
| DELETE | `/cart/items/{itemId}` | — | `200` |
| DELETE | `/cart` | — | `204` |

`userId` **luôn lấy từ token**, không nhận từ client. `itemId` phải kiểm tra thuộc giỏ của user hiện tại, không thì `404` (không phải `403` — trả `403` là xác nhận id đó tồn tại, rò rỉ thông tin).

Thêm vào giỏ **không** giữ hàng. Giữ hàng chỉ xảy ra ở bước checkout — đây là quyết định thiết kế, ghi vào ADR: giữ hàng ngay từ lúc thêm giỏ sẽ khóa tồn kho cho những giỏ bị bỏ quên hàng tuần.

---

## 5. Checkout & Reservation — `/api/v1/checkout`

| Method | Endpoint | Quyền | Request | Response |
|---|---|---|---|---|
| POST | `/checkout/sessions` | CUSTOMER | `{couponCode?}` | `201` `{sessionId, expiresAt, items[], subtotal, discount, total}` — **giữ hàng 10 phút** |
| GET | `/checkout/sessions/{id}` | CUSTOMER | — | `200` + thời gian còn lại |
| DELETE | `/checkout/sessions/{id}` | CUSTOMER | — | `204`, nhả hàng ngay |

Thiếu hàng → `409 INSUFFICIENT_STOCK` kèm `details` liệt kê **từng variant** thiếu bao nhiêu, để client hiển thị chính xác. Trả một câu "hết hàng" chung chung là trải nghiệm tệ và cũng là API thiết kế kém.

---

## 6. Order — `/api/v1/orders`

| Method | Endpoint | Quyền | Request | Response |
|---|---|---|---|---|
| POST | `/orders` | CUSTOMER | `{sessionId, shippingAddress, note?}` + `Idempotency-Key` | `201` `{orderCode, total, status}` |
| GET | `/orders` | CUSTOMER | `?status=&page=&size=` | `200` đơn của **chính** user |
| GET | `/orders/{code}` | CUSTOMER / ADMIN | — | `200` (customer chỉ xem đơn của mình) |
| POST | `/orders/{code}/cancel` | CUSTOMER / ADMIN | `{reason?}` | `200` — **idempotent**, hủy lần hai không hoàn kho lần hai |
| GET | `/orders/{code}/history` | CUSTOMER / ADMIN | — | `200` `order_status_history` |

Định danh đối ngoại dùng `code` (`TM-20260901-0001`), không dùng `id`. Xem `01-domain-v2.md` mục 5.3.

---

## 7. Payment — `/api/v1/payments`

| Method | Endpoint | Quyền | Request | Response |
|---|---|---|---|---|
| POST | `/payments` | CUSTOMER | `{orderCode, method}` + `Idempotency-Key` | `200` `{paymentId, status, transactionRef}` |
| POST | `/payments/webhook` | Public (ký HMAC) | `{transactionRef, status, signature}` | `200` |
| GET | `/payments/{id}` | CUSTOMER / ADMIN | — | `200` |
| POST | `/payments/{id}/refund` | ADMIN | `{amount, reason}` | `200` |

Webhook không có token nhưng **phải xác thực chữ ký HMAC** bằng secret dùng chung. Mock gateway cũng ký — làm đúng như thật để giải thích được, và để không tạo thói quen sai. Webhook phải idempotent: gọi lại cùng `transactionRef` trả `200` mà không đổi trạng thái lần nữa.

---

## 8. Coupon — `/api/v1/coupons`

| Method | Endpoint | Quyền | Request | Response |
|---|---|---|---|---|
| POST | `/coupons/validate` | CUSTOMER | `{code, subtotal}` | `200` `{valid, discountAmount, reason?}` |
| GET | `/coupons` | ADMIN | | `200` phân trang |
| POST | `/coupons` | ADMIN | `{code, type, value, minOrderAmount, maxDiscount, usageLimit, perUserLimit, startsAt, endsAt}` | `201` |
| PUT/DELETE | `/coupons/{id}` | ADMIN | | |

`validate` chỉ để hiển thị trước cho khách — **không giữ chỗ**. Việc trừ lượt dùng thật xảy ra trong transaction đặt hàng, có khóa dòng coupon. Khách thấy "hợp lệ" rồi lúc đặt bị `409 COUPON_USED_UP` là hành vi đúng, không phải bug.

---

## 9. Flash Sale — `/api/v1/flash-sales`

| Method | Endpoint | Quyền | Request | Response |
|---|---|---|---|---|
| GET | `/flash-sales/active` | Public | — | `200` phiên đang chạy + tồn còn lại (đọc từ Redis) |
| GET | `/flash-sales/upcoming` | Public | — | `200` phiên sắp mở + `startsAt` |
| POST | `/flash-sales/{id}/items/{itemId}/purchase` | CUSTOMER | `{quantity}` + `Idempotency-Key` | `201` `{orderCode}` / `409 SOLD_OUT` / `429` |
| POST | `/flash-sales` | ADMIN | `{name, startsAt, endsAt, items[]}` | `201` |
| POST | `/flash-sales/{id}/warmup` | ADMIN | — | `200` — nạp tồn kho vào Redis trước giờ mở bán |

Rate limit endpoint `purchase`: 10 request/phút/user **và** 100/phút/IP. Hai tầng vì một user có thể đổi IP, và một IP (mạng công ty, NAT) có thể có nhiều user thật.

Endpoint `warmup` là chi tiết đáng nói khi phỏng vấn: nếu để lần request đầu tiên tự nạp cache thì đúng giây mở bán sẽ có hàng nghìn request cùng nạp — **cache stampede**. Nạp trước là cách chặn rẻ nhất.

---

## 10. Review — `/api/v1/reviews`

| Method | Endpoint | Quyền | Ghi chú |
|---|---|---|---|
| GET | `/products/{id}/reviews` | Public | Phân trang + phân bố sao |
| POST | `/reviews` | CUSTOMER | `{orderItemId, rating, comment}` — chỉ khi đơn đã `DELIVERED` và `order_item` thuộc user (verified purchase) |
| DELETE | `/reviews/{id}` | Chủ review / ADMIN | |

Ràng buộc `UNIQUE(order_item_id)` chặn review trùng ở tầng DB, không chỉ ở code.

---

## 11. Admin — `/api/v1/admin`

| Method | Endpoint | Quyền | Ghi chú |
|---|---|---|---|
| GET | `/admin/orders` | STAFF / ADMIN | Lọc theo `status`, `dateFrom`, `dateTo`, `keyword` (mã đơn / email khách) |
| PATCH | `/admin/orders/{code}/status` | STAFF / ADMIN | `{toStatus, note}` — đi qua bảng chuyển trạng thái ở `02-luong-nghiep-vu.md` |
| GET | `/admin/inventory/low-stock` | STAFF / ADMIN | `?threshold=10` |
| GET | `/admin/dashboard/revenue` | ADMIN | `?groupBy=day\|month&from=&to=` — **query aggregate**, không load order về app |
| GET | `/admin/dashboard/top-products` | ADMIN | `?limit=10&from=&to=` |
| GET | `/admin/dashboard/summary` | ADMIN | Doanh thu hôm nay, số đơn theo trạng thái, giá trị đơn trung bình |

---

## 12. Serial & Bảo hành — `/api/v1/serials` *(cắt được nếu trễ)*

| Method | Endpoint | Quyền | Ghi chú |
|---|---|---|---|
| GET | `/serials/{serialNo}/warranty` | Public | Tra bảo hành: còn hạn không, mua ngày nào |
| POST | `/serials/bulk` | ADMIN | Nhập lô serial cho một variant |
| POST | `/warranty-requests` | CUSTOMER | `{serialNo, reason}` |
| PATCH | `/warranty-requests/{id}` | STAFF / ADMIN | `{status, note}` |

Đây là phần **hiếm portfolio nào có**. Chi phí thấp, và mở ra một câu hỏi phỏng vấn rất chất: *"Tồn kho theo số lượng khác tồn kho theo serial ở chỗ nào, khi nào cần cái nào?"*

---

## 13. Vận hành

| Endpoint | Quyền | Ghi chú |
|---|---|---|
| `GET /actuator/health` | Public | Readiness/liveness cho nền tảng deploy |
| `GET /actuator/prometheus` | Nội bộ | Prometheus scrape |
| `GET /swagger-ui.html` | Public | Nhóm tag: Auth · Catalog · Cart · Checkout · Order · Payment · Flash Sale · Admin |

Mọi endpoint trên Swagger phải có mô tả và ví dụ request/response. Backend-only thì **Swagger chính là giao diện sản phẩm** — đầu tư nửa ngày vào đây đáng giá hơn nhiều thứ khác.
