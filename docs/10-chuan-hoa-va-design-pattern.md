# Chuẩn hoá CSDL & Design pattern — câu hỏi và đáp án

> Hai câu hỏi gần như chắc chắn được hỏi khi trình bày một project có database. Tài liệu này ghi lại câu trả lời **có dẫn chứng từ code thật**, kèm các câu đào sâu interviewer thường hỏi tiếp.
>
> Nguyên tắc xuyên suốt: **không khoe pattern không có**. Nói được vì sao *không* dùng một thứ thường ăn điểm hơn kể tên một thứ chỉ nghe qua.

---

## Câu hỏi 1: "Database của bạn đã chuẩn hoá chưa? Đạt chuẩn mấy?"

### Đáp án ngắn (30 giây)

> Schema đạt **3NF** làm mặc định. Có đúng **5 chỗ cố ý phá chuẩn**, và chúng thuộc hai loại rất khác nhau: một loại là **snapshot vì ngữ nghĩa** — giá trị *phải* đóng băng, chuẩn hoá đúng sẽ làm sai nghiệp vụ; loại kia là **bộ đếm phi chuẩn hoá vì hiệu năng** — đây mới là đánh đổi thật, và cần cơ chế đối soát đi kèm.

Rồi mới đi vào chi tiết bên dưới. Trả lời "rồi, đạt 3NF" cụt lủn là bỏ mất phần hay nhất.

### 1NF — đạt, trừ 4 cột JSONB

`products.spec`, `product_variants.attributes`, `orders.shipping_address`, `outbox_events.payload`.

Nghiêm ngặt thì JSONB vi phạm 1NF vì giá trị không nguyên tử. Lập luận bảo vệ:

- Tập thuộc tính **thực sự** biến thiên theo ngành hàng: điện thoại có RAM/chipset/camera, laptop có CPU/GPU/trọng lượng. Cột cố định thì mỗi ngành hàng mới là một lần `ALTER TABLE` và một nửa số cột luôn `NULL`.
- Điều 1NF muốn bảo vệ là **truy vấn được**, chứ không phải hình thức. JSONB ở đây có GIN index và lọc bằng `spec @> '{"ram":"16GB"}'` — không phải blob mờ đục. Phép đo ở [06-nfr-va-load-test.md](06-nfr-va-load-test.md): index đưa p95 từ 2.220ms xuống 234ms trên 50.000 sản phẩm.
- Phương án EAV chuẩn hoá đúng 1NF nhưng mỗi bộ lọc thành một self-join, và không index tử tế được.

Chi tiết ở [ADR-0002](adr/0002-jsonb-cho-product-spec.md).

**Nhược điểm phải thừa nhận:** JSONB không có ràng buộc kiểu ở DB — `"ram": "8GB"` và `"ram": 8` cùng lọt. Bù bằng validate ở tầng ứng dụng.

### 2NF — đạt hiển nhiên

Cả 23 bảng đều dùng khoá chính thay thế một cột (`id BIGSERIAL`), nên không tồn tại phụ thuộc bộ phận vào một phần của khoá.

### 3NF / BCNF — đạt ở các bảng tham chiếu, phá có chủ đích ở 5 chỗ

#### Loại A — snapshot vì **ngữ nghĩa**, không phải vì hiệu năng

| Cột | Giải thích |
|---|---|
| `order_items.product_name`, `variant_name`, `sku`, `unit_price` | Phụ thuộc bắc cầu qua `variant_id` → phá 3NF trên giấy. Nhưng **đó chính là mục đích**: giá trị phải đóng băng tại thời điểm mua |
| `orders.shipping_address` | Địa chỉ giao lúc đặt, không phải địa chỉ hiện tại của khách |

**Lập luận chặt hơn — nên dùng khi bị hỏi sâu:** đây *không thực sự* là vi phạm 3NF. `order_items.product_name` không phải bản sao của `products.name`; nó là một thuộc tính riêng với ngữ nghĩa **"tên tại thời điểm bán"**. Hai giá trị chỉ *tình cờ* bằng nhau lúc tạo đơn. Chuẩn hoá "đúng" ở đây sẽ khiến đổi tên hoặc đổi giá sản phẩm làm thay đổi hoá đơn cũ — tức là sai nghiệp vụ.

Có test chứng minh: `changingPrice_doesNotAffectExistingOrders` và `deleteProduct_alreadyInOrder_doesNotBreakOrderHistory`.

#### Loại B — bộ đếm phi chuẩn hoá vì **hiệu năng** (đánh đổi thật)

| Cột | Đáng lẽ tính từ | Vì sao vẫn lưu |
|---|---|---|
| `product_variants.reserved_quantity` | `SUM(inventory_reservations)` | Mỗi lượt xem sản phẩm đều cần `available = stock − reserved`. Tính `SUM` mỗi lần thì trang danh sách chết dưới tải |
| `coupons.used_count` | `COUNT(coupon_redemptions)` | Kiểm hạn mức nằm trong transaction đặt hàng đang giữ khoá — phải nhanh |
| `flash_sale_items.sold_quantity` | Redis / `COUNT(orders)` | Nguồn sự thật lúc phiên chạy là Redis; đây là bản ghi cuối |
| `orders.subtotal`, `total_amount` | `SUM(order_items.line_total)` | Vừa là snapshot vừa là bộ đếm |

Mỗi cột đều có **`CHECK` constraint làm chốt chặn** ở DB:

```sql
CHECK (reserved_quantity <= stock_quantity)   -- product_variants
CHECK (sold_quantity <= sale_quantity)        -- flash_sale_items
CHECK (used_count >= 0)                       -- coupons
```

DB không cho lệch quá xa kể cả khi code sai — đây là lớp phòng thủ cuối, độc lập với logic khoá ở tầng trên.

### Chỗ còn thiếu — nói thẳng, đừng giấu

Phá chuẩn loại B đòi hỏi **job đối soát định kỳ**. Hiện trạng:

| Bộ đếm | Đối soát |
|---|---|
| `flash_sale_items.sold_quantity` | ✅ có — `POST /api/v1/flash-sales/{id}/reconcile` |
| `product_variants.reserved_quantity` | ❌ **chưa làm** |
| `coupons.used_count` | ❌ **chưa làm** |

Khi bị hỏi *"counter lệch thì sao?"*, câu trả lời trung thực là: có `CHECK` chặn ở DB, có đối soát cho flash sale, hai cái còn lại mới dừng ở thiết kế trong [01-domain-v2.md §5.1](01-domain-v2.md). Thừa nhận một khoảng trống cụ thể đáng tin hơn nhiều so với trả lời trôi chảy mà mơ hồ.

### Câu đào sâu thường gặp

| Câu hỏi | Ý chính khi trả lời |
|---|---|
| "Sao không chuẩn hoá hết rồi tính bằng `SUM` khi cần?" | Vì `available = stock − reserved` chạy trên **mọi lượt xem sản phẩm**, không phải chỉ lúc đặt hàng. `SUM` trên bảng reservation ở mỗi request là điểm chết đầu tiên |
| "Denormalize thì làm sao đảm bảo nhất quán?" | Ba lớp: (1) cập nhật hai chỗ trong **cùng transaction**, (2) chỉ sửa qua **một service duy nhất**, (3) `CHECK` constraint ở DB. Lớp thứ tư — job đối soát — mới có cho flash sale |
| "JSONB có nhược gì so với bảng cố định?" | Không ràng buộc kiểu ở DB; sửa một key trên toàn bộ sản phẩm khó hơn `ALTER TABLE`; và nếu một thuộc tính trở thành trục nghiệp vụ chính thì nó nên là cột thật |
| "Khi nào bạn sẽ chuẩn hoá lại?" | Khi bộ đếm bắt đầu lệch thật và job đối soát không đủ; hoặc khi tần suất đọc giảm tới mức `SUM` trực tiếp là chấp nhận được |
| "Có bảng nào chưa đạt BCNF không?" | Không. Các bảng tham chiếu đều có mọi định thức là khoá dự tuyển |

---

## Câu hỏi 2: "Project có dùng design pattern nào không?"

### Đáp án ngắn (30 giây)

> Có, nhưng tôi chỉ kể những cái **nhìn thấy được trong code** chứ không phải nhãn dán. Ba cái đáng nói nhất là **Transactional Outbox**, **State Machine dạng bảng dữ liệu**, và **Rich Domain Model**. Ngoài ra là các pattern nền của Spring và vài pattern về đồng thời.

### Pattern thực sự có trong code

| Pattern | Ở đâu | Điểm đáng nói |
|---|---|---|
| **Transactional Outbox** | [`OutboxRecorder`](../src/main/java/dev/techmart/common/outbox/OutboxRecorder.java), `OutboxWorker` | `propagation = MANDATORY` **ép** caller phải đang ở trong transaction — event không thể tồn tại nếu nghiệp vụ chưa commit. Worker dùng `FOR UPDATE SKIP LOCKED` nên scale ngang được |
| **State Machine** | [`OrderStateMachine`](../src/main/java/dev/techmart/order/domain/OrderStateMachine.java) | Bảng chuyển trạng thái là **dữ liệu** (`Map<Transition, Set<Actor>>`), không phải chuỗi `if`. 7 trạng thái = 42 cặp, chỉ 9 hợp lệ; 33 cặp còn lại tự động bị chặn |
| **Rich Domain Model** (DDD) | 18 method nghiệp vụ nằm *trong* entity | `hold`, `commitHold`, `releaseHold`, `deduct`, `restock`, `consume`, `transitionTo`… Bất biến được kiểm ngay trong entity, không phải anemic model |
| **Aggregate Root** (DDD) | `Order` → `OrderItem`, `Cart` → `CartItem` | Sửa item luôn đi qua root; `cascade` + `orphanRemoval` đặt ở root |
| **Repository** | 19 interface Spring Data | |
| **DTO** | 76 `record` | Entity không bao giờ serialize thẳng ra API — tránh lộ `passwordHash` và lỗi lazy-loading |
| **Idempotency Key** | `POST /orders`, `/payments`, flash sale | Chuẩn Stripe. Chốt chặn là `UNIQUE` ở DB, không phải cache — xem [ADR-0008](adr/0008-idempotency-key.md) |
| **Optimistic / Pessimistic Locking** | `@Version` + `FOR UPDATE ORDER BY id` | Mỗi chỗ một kiểu, có lý do — [ADR-0003](adr/0003-chien-luoc-khoa-ton-kho.md) |
| **Token Bucket** | [`rate-limit.lua`](../src/main/resources/redis/rate-limit.lua) | Trạng thái trên Redis nên đúng khi chạy nhiều instance |
| **Cache-aside** | [`CatalogCache`](../src/main/java/dev/techmart/catalog/service/CatalogCache.java) | Xoá cache bằng **bộ đếm thế hệ** thay vì `SCAN`. Đo được: p95 279ms → 9,7ms, hit rate 92,7% |
| **Unit of Work** | Persistence context + `@Transactional` ở tầng service | Ranh giới giao dịch thuộc về nghiệp vụ, không thuộc controller |
| **Dependency Injection / IoC** | Constructor injection toàn bộ | Không `@Autowired` trên field |
| **Proxy** (Spring AOP) | `@Transactional`, `@PreAuthorize` | Và **bẫy self-invocation** của nó đã cắn 3 lần trong dự án này — xem dưới |

### Bẫy Proxy — chi tiết đáng kể nhất trong nhóm này

Spring AOP hoạt động qua proxy, nên **gọi method trong cùng class không đi qua proxy** và annotation bị bỏ qua hoàn toàn. Trong dự án này nó xuất hiện **ba lần, ba triệu chứng khác nhau**:

1. **Job nhả hàng quá hạn** — method `@Scheduled` gọi sang method `@Transactional` cùng class → ném `TransactionRequiredException` mỗi 30 giây. Sửa: đặt `@Transactional` ngay trên method `@Scheduled`.
2. **Flash sale** — cần gọi Redis *ngoài* transaction rồi ghi DB *trong* transaction. Hai yêu cầu này chỉ thoả mãn được khi tách [`FlashSaleOrderWriter`](../src/main/java/dev/techmart/flashsale/service/FlashSaleOrderWriter.java) thành bean riêng.
3. **Thu hồi refresh token** — nghiêm trọng nhất: `revokeAllForUser()` chạy rồi ném exception ngay sau đó, exception rollback luôn cả lệnh thu hồi. Tính năng bảo mật coi như không tồn tại. Sửa bằng [`TokenRevoker`](../src/main/java/dev/techmart/identity/service/TokenRevoker.java) với `REQUIRES_NEW`. Test `refresh_reusedToken_revokesEntireChain` bắt được.

Kể được ba trường hợp này cho thấy bạn hiểu *cơ chế* chứ không chỉ thuộc tên annotation.

### Cái **không** có — để không bị hỏi hớ

Không dùng, và biết mình không dùng: **Strategy**, **Factory**, **Builder**, **Observer** theo nghĩa GoF (ngoài những gì Spring tự làm) · **Circuit Breaker** · **CQRS** · **Saga**.

Lý do từng cái, vì "chưa nghĩ tới" và "đã cân nhắc rồi loại" là hai câu trả lời rất khác nhau:

| Pattern | Vì sao không |
|---|---|
| **Saga** | Giải bài toán giao dịch trải qua nhiều service/database không có transaction chung. Ở đây tất cả nằm trong một PostgreSQL và một `@Transactional`. Saga sẽ giải một vấn đề đã cố ý tránh bằng [ADR-0001](adr/0001-monolith-modular.md) — *"em chọn monolith nên có transaction thật; nếu tách `inventory` ra thì Saga thành bắt buộc, đó là một phần chi phí của việc tách"* |
| **Circuit Breaker** | Cần một lời gọi **từ xa** có thể treo. Cổng thanh toán là mock chạy cùng tiến trình. Trường hợp thật duy nhất là Redis chậm, nhưng Lettuce đã có timeout 2s và rate limit đã fail-open |
| **CQRS** | **Phần hữu ích đã có**: [`DashboardRepository`](../src/main/java/dev/techmart/admin/repository/DashboardRepository.java) dùng native aggregate query tách hẳn khỏi domain model — đường đọc và đường ghi đã khác nhau. CQRS đầy đủ với hai kho dữ liệu thì vượt xa nhu cầu |
| **Observer** | Chính là `@TransactionalEventListener`, và nó nằm trong [ADR-0005](adr/0005-outbox-thay-message-broker.md) như **phương án bị loại**: event sống trong bộ nhớ, app chết sau commit là mất event mà không ai biết |
| **Strategy** | Ba chiến lược khoá không thay thế nhau lúc chạy mà gắn cứng với ba luồng nghiệp vụ. Chỗ hợp lý duy nhất là discount engine — hiện `Coupon.discountFor()` là `switch` trên 2 loại, và với 2 loại thì `switch` gọn hơn Strategy |
| **Factory / Builder** | Đã có ở dạng nhẹ (`OrderCodeGenerator`). Record + constructor 6 tham số đủ dùng; Builder dành cho object nhiều tham số tuỳ chọn |

Câu chốt khi bị hỏi "sao không dùng thêm pattern X": **pattern giải quyết vấn đề; ở đây không có vấn đề đó.** Gắn đủ tám pattern vào để dài CV chính là thứ interviewer giỏi soi ra đầu tiên.

### Câu đào sâu thường gặp

| Câu hỏi | Ý chính khi trả lời |
|---|---|
| "Outbox khác gì dùng RabbitMQ?" | **Broker không thay thế được outbox.** Kể cả có RabbitMQ vẫn cần outbox để "ghi đơn hàng" và "publish sự kiện" là nguyên tử. Broker chỉ thay phần vận chuyển |
| "Sao không để logic trong service cho gọn?" | Bất biến nghiệp vụ đặt trong entity thì **không đường nào lách được**. `ProductVariant.hold()` tự kiểm `available >= quantity`, không phụ thuộc việc service có nhớ kiểm hay không |
| "State machine sao không dùng thư viện?" | Bảng 9 dòng không đáng thêm phụ thuộc. Nếu có guard/action phức tạp hoặc cần trạng thái lồng nhau thì Spring State Machine đáng giá |
| "Aggregate root là gì, ranh giới ở đâu?" | `Order` là root, `OrderItem` không tồn tại độc lập. Ranh giới trùng với ranh giới transaction: một transaction sửa một aggregate |
| "Có anti-pattern nào bạn cố tránh không?" | Anemic domain model; `@Transactional` ở controller; serialize entity ra API; bắt `Exception` rồi nuốt; `EAGER` fetch để chữa `LazyInitializationException` |

---

## Cách dùng tài liệu này

Đọc lại trước buổi phỏng vấn, nhưng **đừng học thuộc**. Thứ cần nhớ là ba ý:

1. Chuẩn hoá là mặc định; phá chuẩn phải **nêu được lý do và cơ chế bù**.
2. Phân biệt phá chuẩn vì **ngữ nghĩa** với phá chuẩn vì **hiệu năng** — hai thứ hoàn toàn khác nhau.
3. Chỉ kể pattern **chỉ được vào code**; nói được vì sao *không* dùng một pattern thường ăn điểm hơn kể tên nó.
