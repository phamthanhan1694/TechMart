> ⚠️ **Tài liệu v1 — không code theo bản này.** Bản thiết kế thi hành là `01-domain-v2.md`. Giữ lại đây để tham khảo phần lý thuyết (BigDecimal, snapshot giá, N+1 query, optimistic lock).

# Đặc tả E-commerce Backend — Entity & API

> Tài liệu để code theo. Gồm: đặc tả từng entity (trường, kiểu, ràng buộc JPA) và toàn bộ API endpoint kèm phân quyền.

---

## Phần A — Đặc tả Entity

### Nguyên tắc chung (đọc trước)

- **Tiền luôn dùng `BigDecimal`, không bao giờ `double`/`float`.** Số thực dấu phẩy động làm tròn sai khi tính tiền. Đây là lỗi kinh điển và là câu hỏi phỏng vấn hay gặp.
- **Quan hệ collection mặc định `FetchType.LAZY`.** Tránh tải thừa dữ liệu và giảm nguy cơ N+1 query.
- **Mọi entity có `id` kiểu `Long`, khóa chính, tự sinh** (`@Id @GeneratedValue(strategy = GenerationType.IDENTITY)`).
- **Thêm `createdAt` / `updatedAt`** cho các entity chính (dùng `@CreationTimestamp`, `@UpdateTimestamp` của Hibernate).

---

### 1. User

| Trường | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| `id` | `Long` | PK, auto | |
| `email` | `String` | not null, unique | dùng để đăng nhập |
| `password` | `String` | not null | lưu **hash BCrypt**, không bao giờ lưu thô |
| `fullName` | `String` | | |
| `role` | `Role` (enum) | not null | `@Enumerated(EnumType.STRING)` |
| `createdAt` | `Instant` | | |

**Quan hệ:**
- `@OneToOne(mappedBy = "user")` → `Cart` (một user có một giỏ)
- `@OneToMany(mappedBy = "user")` → `List<Order>` (một user có nhiều đơn)

**Enum `Role`:** `CUSTOMER`, `ADMIN`.

---

### 2. Category

| Trường | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| `id` | `Long` | PK, auto | |
| `name` | `String` | not null, unique | |
| `description` | `String` | | |

**Quan hệ:**
- `@OneToMany(mappedBy = "category")` → `List<Product>`

---

### 3. Product

| Trường | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| `id` | `Long` | PK, auto | |
| `name` | `String` | not null | |
| `description` | `String` | `@Column(columnDefinition = "TEXT")` | mô tả dài |
| `price` | `BigDecimal` | not null | **BigDecimal**, không dùng double |
| `stockQuantity` | `Integer` | not null | số lượng tồn |
| `imageUrl` | `String` | nullable | đường dẫn ảnh (nếu làm upload) |
| `version` | `Long` | `@Version` | **khóa lạc quan (optimistic lock)** — chống bán quá tồn |
| `createdAt` | `Instant` | | |
| `updatedAt` | `Instant` | | |

**Quan hệ:**
- `@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "category_id")` → `Category`

**Điểm cốt lõi:** trường `version` với `@Version` là cơ chế optimistic locking. Khi hai transaction cùng sửa một product, Hibernate so version; cái nào commit sau với version cũ sẽ bị `OptimisticLockException`, buộc retry. Đây là một trong hai cách chống race condition khi trừ tồn kho.

---

### 4. Cart

| Trường | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| `id` | `Long` | PK, auto | |

**Quan hệ:**
- `@OneToOne @JoinColumn(name = "user_id")` → `User`
- `@OneToMany(mappedBy = "cart", cascade = ALL, orphanRemoval = true)` → `List<CartItem>`

**Ghi chú:** tổng tiền giỏ hàng **không lưu trong DB** — tính động khi cần (cộng `price × quantity` các item). `cascade = ALL` + `orphanRemoval = true` để khi xóa item khỏi list thì item bị xóa khỏi DB.

---

### 5. CartItem

| Trường | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| `id` | `Long` | PK, auto | |
| `quantity` | `Integer` | not null, ≥ 1 | |

**Quan hệ:**
- `@ManyToOne @JoinColumn(name = "cart_id")` → `Cart`
- `@ManyToOne @JoinColumn(name = "product_id")` → `Product`

---

### 6. Order

| Trường | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| `id` | `Long` | PK, auto | |
| `totalAmount` | `BigDecimal` | not null | **chốt tại thời điểm đặt** |
| `status` | `OrderStatus` (enum) | not null | `@Enumerated(EnumType.STRING)` |
| `shippingAddress` | `String` | | |
| `createdAt` | `Instant` | | |

**Quan hệ:**
- `@ManyToOne @JoinColumn(name = "user_id")` → `User`
- `@OneToMany(mappedBy = "order", cascade = ALL)` → `List<OrderItem>`

**Enum `OrderStatus`:** `PENDING`, `PAID`, `SHIPPED`, `DELIVERED`, `CANCELLED`.

---

### 7. OrderItem

| Trường | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| `id` | `Long` | PK, auto | |
| `quantity` | `Integer` | not null | |
| `priceAtPurchase` | `BigDecimal` | not null | **giá tại lúc mua (snapshot)** |

**Quan hệ:**
- `@ManyToOne @JoinColumn(name = "order_id")` → `Order`
- `@ManyToOne @JoinColumn(name = "product_id")` → `Product`

**Điểm cốt lõi:** `priceAtPurchase` lưu lại giá **tại thời điểm đặt hàng**, không tham chiếu giá hiện tại của `Product`. Lý do: giá sản phẩm có thể đổi sau này, nhưng đơn hàng cũ phải giữ nguyên giá lúc mua. Bỏ qua điểm này là lỗi thiết kế hay gặp.

---

## Phần B — Đặc tả API Endpoint

Tiền tố chung: `/api`. Cột "Quyền": `Public` = không cần đăng nhập, `CUSTOMER`/`ADMIN` = cần token và đúng role.

### Auth — `/api/auth`

| Method | Endpoint | Quyền | Request | Response |
|---|---|---|---|---|
| POST | `/register` | Public | `{email, password, fullName}` | `201` + thông tin user (không kèm password) |
| POST | `/login` | Public | `{email, password}` | `200` + `{accessToken, tokenType}` |

### Category — `/api/categories`

| Method | Endpoint | Quyền | Request | Response |
|---|---|---|---|---|
| GET | `/categories` | Public | — | `200` + danh sách category |
| GET | `/categories/{id}` | Public | — | `200` + chi tiết / `404` |
| POST | `/categories` | ADMIN | `{name, description}` | `201` + category |
| PUT | `/categories/{id}` | ADMIN | `{name, description}` | `200` + category |
| DELETE | `/categories/{id}` | ADMIN | — | `204` |

### Product — `/api/products`

| Method | Endpoint | Quyền | Request | Response |
|---|---|---|---|---|
| GET | `/products` | Public | query params (xem dưới) | `200` + trang sản phẩm |
| GET | `/products/{id}` | Public | — | `200` + chi tiết / `404` |
| POST | `/products` | ADMIN | `{name, description, price, stockQuantity, categoryId, imageUrl}` | `201` + product |
| PUT | `/products/{id}` | ADMIN | như trên | `200` + product |
| DELETE | `/products/{id}` | ADMIN | — | `204` |

**Query params cho `GET /products`** (phân trang + lọc + tìm kiếm):
- `page` (mặc định 0), `size` (mặc định 20), `sort` (vd `price,asc`)
- `keyword` — tìm theo tên
- `categoryId` — lọc theo danh mục
- `minPrice`, `maxPrice` — lọc khoảng giá

Response trả về cấu trúc phân trang chuẩn của Spring (`content`, `totalElements`, `totalPages`, `number`, `size`).

### Cart — `/api/cart` (đều cần CUSTOMER)

| Method | Endpoint | Quyền | Request | Response |
|---|---|---|---|---|
| GET | `/cart` | CUSTOMER | — | `200` + giỏ hàng của user hiện tại + tổng tiền |
| POST | `/cart/items` | CUSTOMER | `{productId, quantity}` | `200` + giỏ đã cập nhật |
| PUT | `/cart/items/{itemId}` | CUSTOMER | `{quantity}` | `200` + giỏ đã cập nhật |
| DELETE | `/cart/items/{itemId}` | CUSTOMER | — | `200` + giỏ đã cập nhật |
| DELETE | `/cart` | CUSTOMER | — | `204` (xóa sạch giỏ) |

Lưu ý: lấy user hiện tại từ token (security context), **không** nhận `userId` từ client — tránh việc user thao tác giỏ của người khác.

### Order — `/api/orders`

| Method | Endpoint | Quyền | Request | Response |
|---|---|---|---|---|
| POST | `/orders` | CUSTOMER | `{shippingAddress}` | `201` + đơn hàng tạo từ giỏ hiện tại |
| GET | `/orders` | CUSTOMER | — | `200` + danh sách đơn của chính user |
| GET | `/orders/{id}` | CUSTOMER / ADMIN | — | `200` + chi tiết (customer chỉ xem đơn của mình) |
| PUT | `/orders/{id}/status` | ADMIN | `{status}` | `200` + đơn đã đổi trạng thái |
| POST | `/orders/{id}/cancel` | CUSTOMER | — | `200` + đơn đã hủy (hoàn lại tồn kho) |

**`POST /orders` là endpoint quan trọng nhất** — đây là nơi logic lõi chạy:
1. Lấy giỏ hàng của user hiện tại.
2. Trong **một** `@Transactional`: kiểm tra tồn kho từng item, trừ tồn (có khóa chống race condition), tạo `Order` + các `OrderItem` (lưu `priceAtPurchase`), tính `totalAmount`, dọn giỏ.
3. Nếu bất kỳ sản phẩm nào không đủ tồn → ném `OutOfStockException`, rollback toàn bộ.

---

## Phần C — Quyết định thiết kế & câu chuyện phỏng vấn

Mỗi điểm dưới đây vừa là lựa chọn kỹ thuật đúng, vừa là thứ bạn có thể trình bày khi phỏng vấn:

1. **`BigDecimal` cho tiền.** Vì sao không dùng `double`: sai số dấu phẩy động khi cộng dồn tiền.

2. **`@Version` (optimistic) vs khóa bi quan (pessimistic) khi trừ tồn.** Hiểu trade-off: optimistic nhẹ, hợp khi ít tranh chấp, nhưng cần retry khi đụng; pessimistic (`SELECT ... FOR UPDATE` qua `@Lock(PESSIMISTIC_WRITE)`) khóa dòng chắc chắn nhưng giảm thông lượng. Chọn một, giải thích được vì sao.

3. **`priceAtPurchase` snapshot trong `OrderItem`.** Đơn hàng cũ không được đổi giá khi product đổi giá. Đây là nguyên tắc "đơn hàng là bản ghi lịch sử bất biến".

4. **Lấy user từ token, không nhận `userId` từ client.** Tránh lỗ hổng truy cập tài nguyên người khác (IDOR).

5. **DTO tách khỏi Entity.** Không serialize thẳng entity (lộ password, lỗi lazy-loading). Map qua tầng `mapper/`.

6. **`@Transactional` ở tầng Service, không ở Controller.** Ranh giới giao dịch thuộc về logic nghiệp vụ.

7. **Cảnh giác N+1 query.** Khi load danh sách order kèm item, dùng `JOIN FETCH` hoặc `@EntityGraph` thay vì để Hibernate bắn N+1 truy vấn.
