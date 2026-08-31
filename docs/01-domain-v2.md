# Đặc tả Domain v2 — Bản thiết kế thi hành

> **Đây là bản thiết kế duy nhất được code theo.** `dac-ta-ecommerce-backend.md` và `erd-ecommerce.md` là v1, chỉ giữ lại để tham khảo phần lý thuyết.
> Toàn bộ schema dưới đây phải nằm trong `V1__init.sql` ngay từ migration đầu tiên, kể cả các bảng tuần 3–4 mới dùng tới.

---

## 1. Khác biệt v1 → v2 (và vì sao)

| v1 | v2 | Lý do |
|---|---|---|
| `products.stock_quantity` | `product_variants.stock_quantity` | iPhone 15 bản 128GB và 256GB có giá và tồn kho khác nhau. Tồn kho thuộc về **variant**, không thuộc về product |
| Không có | `product_variants.reserved_quantity` + `inventory_reservations` | Giữ hàng khi checkout — điểm nhấn kỹ thuật #1 |
| `DELETE` xóa cứng | `deleted_at` (soft delete) | Xóa cứng product đã nằm trong đơn cũ sẽ vi phạm khóa ngoại. **Đây là lỗi thật trong bản v1** |
| `order_items` chỉ snapshot giá | Snapshot thêm `product_name`, `sku`, `variant_name` | Đơn cũ phải in được hóa đơn mà không cần join sang `products` |
| Không có | `orders.idempotency_key` (unique) | Chống tạo trùng đơn khi double-click / retry mạng |
| Không có | `refresh_tokens` | Tài liệu v1 nói "JWT + refresh" nhưng không có chỗ lưu, không revoke được |
| Không có | `order_status_history` | Truy vết ai đổi trạng thái lúc nào — nghiệp vụ thật đều có |
| Không có | `outbox_events` | Xử lý bất đồng bộ đáng tin cậy, không cần message broker |

---

## 2. ERD

```mermaid
erDiagram
  USERS ||--o| CARTS : has
  USERS ||--o{ ORDERS : places
  USERS ||--o{ REFRESH_TOKENS : owns
  CATEGORIES ||--o{ PRODUCTS : groups
  BRANDS ||--o{ PRODUCTS : makes
  PRODUCTS ||--o{ PRODUCT_VARIANTS : has
  PRODUCTS ||--o{ PRODUCT_IMAGES : has
  PRODUCTS ||--o{ REVIEWS : receives
  CARTS ||--o{ CART_ITEMS : contains
  PRODUCT_VARIANTS ||--o{ CART_ITEMS : in
  PRODUCT_VARIANTS ||--o{ INVENTORY_RESERVATIONS : held_by
  PRODUCT_VARIANTS ||--o{ ORDER_ITEMS : in
  PRODUCT_VARIANTS ||--o{ FLASH_SALE_ITEMS : offered_in
  PRODUCT_VARIANTS ||--o{ PRODUCT_SERIALS : tracked_by
  ORDERS ||--o{ ORDER_ITEMS : contains
  ORDERS ||--o{ ORDER_STATUS_HISTORY : logs
  ORDERS ||--o{ PAYMENTS : paid_by
  ORDERS ||--o{ INVENTORY_RESERVATIONS : consumes
  ORDERS ||--o{ COUPON_REDEMPTIONS : uses
  COUPONS ||--o{ COUPON_REDEMPTIONS : redeemed_as
  FLASH_SALES ||--o{ FLASH_SALE_ITEMS : contains
  ORDER_ITEMS ||--o| REVIEWS : enables
  ORDER_ITEMS ||--o{ PRODUCT_SERIALS : assigned

  USERS {
    Long id PK
    String email UK
    String password_hash
    String full_name
    String phone
    Role role
    UserStatus status
    Instant created_at
  }
  REFRESH_TOKENS {
    Long id PK
    Long user_id FK
    String token_hash UK
    Instant expires_at
    Instant revoked_at
    Long replaced_by
  }
  CATEGORIES {
    Long id PK
    String name
    String slug UK
    Long parent_id FK
  }
  BRANDS {
    Long id PK
    String name
    String slug UK
  }
  PRODUCTS {
    Long id PK
    Long category_id FK
    Long brand_id FK
    String name
    String slug UK
    String description
    String thumbnail_url
    Jsonb spec
    ProductStatus status
    Instant deleted_at
  }
  PRODUCT_VARIANTS {
    Long id PK
    Long product_id FK
    String sku UK
    String name
    BigDecimal price
    BigDecimal compare_at_price
    Integer stock_quantity
    Integer reserved_quantity
    Jsonb attributes
    Long version
    Instant deleted_at
  }
  PRODUCT_IMAGES {
    Long id PK
    Long product_id FK
    String url
    Integer sort_order
  }
  CARTS {
    Long id PK
    Long user_id FK_UK
  }
  CART_ITEMS {
    Long id PK
    Long cart_id FK
    Long variant_id FK
    Integer quantity
  }
  INVENTORY_RESERVATIONS {
    Long id PK
    Long variant_id FK
    Long user_id FK
    Long order_id FK
    Integer quantity
    ReservationStatus status
    Instant expires_at
  }
  ORDERS {
    Long id PK
    String code UK
    Long user_id FK
    OrderStatus status
    BigDecimal subtotal
    BigDecimal discount_amount
    BigDecimal shipping_fee
    BigDecimal total_amount
    String coupon_code
    Jsonb shipping_address
    String idempotency_key UK
    Long version
    Instant placed_at
  }
  ORDER_ITEMS {
    Long id PK
    Long order_id FK
    Long variant_id FK
    String product_name
    String variant_name
    String sku
    BigDecimal unit_price
    Integer quantity
    BigDecimal line_total
  }
  ORDER_STATUS_HISTORY {
    Long id PK
    Long order_id FK
    OrderStatus from_status
    OrderStatus to_status
    Long changed_by FK
    String note
    Instant created_at
  }
  PAYMENTS {
    Long id PK
    Long order_id FK
    PaymentMethod method
    PaymentStatus status
    BigDecimal amount
    String transaction_ref UK
    String idempotency_key UK
    Instant paid_at
  }
  COUPONS {
    Long id PK
    String code UK
    DiscountType type
    BigDecimal value
    BigDecimal min_order_amount
    BigDecimal max_discount
    Integer usage_limit
    Integer used_count
    Integer per_user_limit
    Instant starts_at
    Instant ends_at
    Long version
  }
  COUPON_REDEMPTIONS {
    Long id PK
    Long coupon_id FK
    Long user_id FK
    Long order_id FK
  }
  FLASH_SALES {
    Long id PK
    String name
    Instant starts_at
    Instant ends_at
    FlashSaleStatus status
  }
  FLASH_SALE_ITEMS {
    Long id PK
    Long flash_sale_id FK
    Long variant_id FK
    BigDecimal sale_price
    Integer sale_quantity
    Integer sold_quantity
    Integer per_user_limit
    Long version
  }
  REVIEWS {
    Long id PK
    Long product_id FK
    Long user_id FK
    Long order_item_id FK_UK
    Integer rating
    String comment
  }
  PRODUCT_SERIALS {
    Long id PK
    Long variant_id FK
    String serial_no UK
    SerialStatus status
    Long order_item_id FK
    Integer warranty_months
    Instant activated_at
  }
  WARRANTY_REQUESTS {
    Long id PK
    Long serial_id FK
    Long user_id FK
    WarrantyStatus status
    String reason
  }
  OUTBOX_EVENTS {
    Long id PK
    String aggregate_type
    Long aggregate_id
    String event_type
    Jsonb payload
    OutboxStatus status
    Integer retry_count
    Instant processed_at
  }
```

---

## 3. Nguyên tắc chung khi hiện thực hóa

- **Tiền luôn `BigDecimal`**, cột `NUMERIC(15,2)`. Không bao giờ `double`/`float`.
- **Mọi quan hệ collection `FetchType.LAZY`.** Cần dữ liệu kèm thì dùng `JOIN FETCH` / `@EntityGraph`, không đổi sang EAGER.
- **Không serialize entity ra API.** DTO là `record`, map ở tầng service (thủ công hoặc MapStruct).
- **`@Transactional` ở tầng Service**, không ở Controller.
- **Thời gian dùng `Instant`**, cột `TIMESTAMPTZ`, lưu UTC. Quy đổi múi giờ là việc của client.
- **Soft delete:** `deleted_at IS NULL` là điều kiện mặc định của mọi truy vấn public. Cân nhắc `@SQLRestriction` của Hibernate 6 để khỏi quên.
- **Enum lưu dạng chuỗi** (`@Enumerated(EnumType.STRING)`). Lưu ordinal là quả bom hẹn giờ khi thêm giá trị mới.

---

## 4. Các enum

| Enum | Giá trị |
|---|---|
| `Role` | `CUSTOMER`, `STAFF`, `ADMIN` |
| `UserStatus` | `ACTIVE`, `LOCKED` |
| `ProductStatus` | `DRAFT`, `ACTIVE`, `ARCHIVED` |
| `OrderStatus` | `PENDING_PAYMENT`, `PAID`, `PACKED`, `SHIPPED`, `DELIVERED`, `CANCELLED`, `REFUNDED` |
| `PaymentStatus` | `INITIATED`, `SUCCEEDED`, `FAILED`, `REFUNDED` |
| `PaymentMethod` | `MOCK_CARD`, `COD` |
| `ReservationStatus` | `HELD`, `COMMITTED`, `RELEASED`, `EXPIRED` |
| `DiscountType` | `PERCENT`, `FIXED` |
| `FlashSaleStatus` | `SCHEDULED`, `RUNNING`, `ENDED` |
| `SerialStatus` | `IN_STOCK`, `SOLD`, `RETURNED`, `DEFECTIVE` |
| `WarrantyStatus` | `REQUESTED`, `APPROVED`, `REJECTED`, `COMPLETED` |
| `OutboxStatus` | `PENDING`, `PROCESSED`, `FAILED` |

---

## 5. Ba điểm thiết kế cần giải thích được khi phỏng vấn

### 5.1. `reserved_quantity` trên variant **và** bảng `inventory_reservations` — sao lại có cả hai?

`inventory_reservations` là **nguồn sự thật** (biết ai giữ, giữ bao nhiêu, hết hạn khi nào, để audit và để nhả đúng).
`product_variants.reserved_quantity` là **bộ đếm phi chuẩn hóa** (denormalized counter), cập nhật trong **cùng transaction** với bảng reservation.

Lý do có counter: câu truy vấn `available = stock_quantity - reserved_quantity` chạy trên mọi lượt xem sản phẩm. Nếu mỗi lần phải `SUM(quantity)` trên `inventory_reservations` thì trang danh sách sản phẩm sẽ chết dưới tải.

**Đánh đổi:** hai chỗ ghi phải luôn đồng bộ. Chặn bằng: (a) chỉ sửa qua một service duy nhất, (b) cùng transaction, (c) một job đối soát chạy định kỳ so counter với `SUM` và log lệch.

> Câu hỏi phỏng vấn sẽ tới: *"Nếu counter lệch thì sao?"* — trả lời bằng job đối soát ở trên. Trả lời được câu này là điểm cộng lớn.

### 5.2. Spec để `products.spec` (JSONB), thuộc tính variant để `product_variants.attributes` (JSONB) — khác nhau ở đâu?

- `products.spec` = thông số **chung của cả dòng máy**: chip, kích thước màn hình, camera. Dùng để lọc và so sánh.
- `product_variants.attributes` = thứ **tạo ra variant**: `{"color": "Titan đen", "storage": "256GB"}`. Dùng để hiển thị lựa chọn.

Chọn JSONB thay vì EAV: EAV linh hoạt tối đa nhưng mọi truy vấn lọc biến thành chuỗi self-join, và không đánh index tử tế được. JSONB + GIN index cho phép `spec @> '{"ram":"8GB"}'` chạy nhanh mà vẫn giữ được sự linh hoạt.

**Nhược điểm phải thừa nhận** (interviewer sẽ hỏi): JSONB không có ràng buộc kiểu ở DB — `"ram": "8GB"` và `"ram": 8` cùng lọt. Cách bù: validate bằng schema ở tầng ứng dụng theo `category`, và có bảng `attribute_definitions` nếu muốn admin tự khai báo.

### 5.3. `orders.code` tách khỏi `orders.id`

`id` là khóa kỹ thuật, tăng dần. `code` là mã hiển thị cho khách (`TM-20260901-0001`). Không phơi `id` tuần tự ra ngoài — nó tiết lộ số đơn hàng mỗi ngày của cửa hàng, và tạo cơ hội dò tuần tự.

---

## 6. Ràng buộc DB bắt buộc (không chỉ validate ở ứng dụng)

| Bảng | Ràng buộc | Chặn điều gì |
|---|---|---|
| `orders` | `UNIQUE(idempotency_key)` | Tạo trùng đơn khi double-click / retry |
| `payments` | `UNIQUE(idempotency_key)`, `UNIQUE(transaction_ref)` | Trừ tiền hai lần |
| `cart_items` | `UNIQUE(cart_id, variant_id)` | Một variant nằm hai dòng trong cùng giỏ |
| `coupon_redemptions` | `UNIQUE(coupon_id, user_id, order_id)` | Một user dùng lại mã trên cùng đơn |
| `reviews` | `UNIQUE(order_item_id)` | Review nhiều lần cùng một lần mua |
| `product_variants` | `CHECK (stock_quantity >= 0)`, `CHECK (reserved_quantity >= 0)`, `CHECK (reserved_quantity <= stock_quantity)` | **Chốt chặn cuối cùng của oversell.** Nếu logic khóa sai, DB vẫn ném lỗi thay vì để tồn kho âm |
| `cart_items` | `CHECK (quantity >= 1)` | Số lượng 0 hoặc âm |
| `flash_sale_items` | `CHECK (sold_quantity <= sale_quantity)` | Bán quá suất flash sale |
| `carts` | `UNIQUE(user_id)` | Một user hai giỏ |

> `CHECK (reserved_quantity <= stock_quantity)` là ràng buộc quan trọng nhất bảng này. Khi viết concurrency test, hãy tạm gỡ khóa ở tầng code và cho test chạy — DB sẽ ném lỗi. Đó là cách chứng minh bạn phòng thủ nhiều lớp, không đặt cược tất cả vào một cơ chế.

---

## 7. Bốn chỗ dễ dính N+1 query

Biết trước để xử lý luôn, và để trả lời khi được hỏi "N+1 xảy ra ở đâu trong project của bạn":

1. `GET /orders` → mỗi order load `order_items` → dùng `@EntityGraph(attributePaths = {"items"})`
2. `GET /products` → mỗi product load `variants` để tính giá thấp nhất → `JOIN FETCH` hoặc query riêng trả `Map<productId, minPrice>`
3. Trang chi tiết giỏ hàng → mỗi `cart_item` load `variant` rồi load `product` → `JOIN FETCH` hai cấp
4. Dashboard doanh thu → **không** load order về app rồi cộng; dùng query aggregate (`SUM`, `GROUP BY date_trunc`) trả thẳng DTO

Bật `spring.jpa.properties.hibernate.generate_statistics=true` ở profile `dev` và đếm số query — có số liệu cụ thể để kể thì thuyết phục hơn nhiều.

---

## 8. Kế hoạch index

| Index | Phục vụ |
|---|---|
| `products(category_id) WHERE deleted_at IS NULL` | Lọc theo danh mục |
| `products(brand_id) WHERE deleted_at IS NULL` | Lọc theo hãng |
| GIN trên `products.spec` | `spec @> '{"ram":"8GB"}'` |
| GIN trigram trên `products.name` | Tìm theo tên (`pg_trgm`, `ILIKE '%...%'`) |
| `product_variants(product_id)` | Load variant theo product |
| `product_variants(price)` | Lọc khoảng giá / sort theo giá |
| `orders(user_id, created_at DESC)` | Danh sách đơn của user |
| `orders(status, created_at DESC)` | Màn hình admin lọc theo trạng thái |
| `order_items(order_id)` | Load item của đơn |
| `inventory_reservations(expires_at) WHERE status = 'HELD'` | **Job quét reservation quá hạn** — thiếu index này job sẽ full-scan mỗi 30 giây |
| `outbox_events(status, created_at) WHERE status = 'PENDING'` | Worker outbox |
| `refresh_tokens(token_hash)` | Tra token mỗi lần refresh |

Tuần 4 chạy `EXPLAIN ANALYZE` cho ba truy vấn nặng nhất, chụp lại kết quả trước và sau khi thêm index — đưa vào README.
