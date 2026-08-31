# Đóng gói để phỏng vấn — CV, demo, ngân hàng câu hỏi

> Project chỉ có giá trị bằng phần bạn kể được về nó. Tài liệu này biến 5 tuần code thành 30 phút nói chuyện thuyết phục.
> Viết ở tuần 5, nhưng **đọc lướt ngay từ tuần 1** để biết mình đang tích lũy nguyên liệu gì.

---

## 1. Bullet CV

Bốn dòng, mỗi dòng một con số thật. Điền số sau khi có kết quả ở `06-nfr-va-load-test.md` — **không bịa số**.

> **TechMart — E-commerce Backend** · Java 21, Spring Boot 3, PostgreSQL, Redis, Docker · [github.com/...] · [demo]
> - Thiết kế backend thương mại điện tử thiết bị điện tử: catalog đa biến thể với thuộc tính động (JSONB + GIN index), giỏ hàng, đặt hàng, thanh toán mock và trang quản trị; 25+ endpoint, tài liệu OpenAPI đầy đủ.
> - Xử lý **chống bán quá tồn kho dưới tải đồng thời** bằng pessimistic locking cho đơn thường và Redis Lua atomic cho flash sale; test 500 request đồng thời vào 50 suất bán cho ra **đúng 50 đơn thành công, 0 lỗi 5xx** (trước khi tối ưu: oversell 13 đơn).
> - Giảm **p95 của API tìm kiếm từ 380ms xuống 42ms** (200 VU, k6) bằng GIN index cho truy vấn JSONB, sửa N+1 query và Redis cache-aside.
> - Xây cơ chế giữ hàng có hạn dùng (reservation TTL) với DB là nguồn sự thật và Redis là lớp tăng tốc — hệ thống **vẫn hoạt động đúng khi Redis chết**; kiểm chứng bằng integration test tắt Redis giữa chừng.

Nguyên tắc: mỗi bullet là **vấn đề → cách giải → con số**. Bullet không có số là bullet bị bỏ qua.

Bullet 4 là bullet phân biệt bạn với phần còn lại: nó nói về **chế độ hỏng hóc**, thứ chỉ người đã nghĩ nghiêm túc mới viết ra.

---

## 2. Dàn ý README

Interviewer đọc README trước khi mở code, và thường chỉ đọc 3 phút. Thứ tự này tối ưu cho 3 phút đó:

1. **Một câu mô tả** + ảnh chụp Swagger hoặc GIF demo (đặt ngay đầu, trên cả mục lục)
2. **Link demo + tài khoản test** — để người ta bấm được ngay
3. **Bài toán** — 3 câu: bán đồ điện tử, hàng giá trị cao số lượng ít, nhiều người tranh mua cùng lúc
4. **Ba điểm nhấn kỹ thuật** — mỗi cái 4–5 dòng, có link tới file code và file test tương ứng
5. **Bảng kết quả đo** (từ `06-nfr-va-load-test.md`)
6. **Kiến trúc** — một sơ đồ mermaid
7. **ERD** — sơ đồ từ `01-domain-v2.md`
8. **Chạy local** — `docker compose up`, đúng một lệnh
9. **Quyết định thiết kế** — link tới `docs/adr/`
10. **Những gì chưa làm và vì sao** ← đừng bỏ mục này

Mục 10 là mục nhiều người bỏ và là mục ăn điểm bất ngờ nhất. Viết thật:

> **Chưa làm, có chủ đích:** tích hợp cổng thanh toán thật (mock gateway đủ để thể hiện luồng idempotency, tích hợp thật không thêm chiều sâu kỹ thuật) · microservices (monolith module hóa phù hợp với quy mô này; nếu tách, tôi sẽ tách `inventory` trước vì nó có ràng buộc đồng thời riêng biệt) · frontend (dự án tập trung vào backend) · thông báo realtime.

Đoạn này chứng minh bạn **biết mình đang đánh đổi cái gì** — khác hẳn người không làm vì không nghĩ tới.

---

## 3. Kịch bản demo 5 phút

Tập chạy trước ít nhất hai lần. Mở sẵn ba tab: Swagger, terminal, Grafana.

| Phút | Nội dung | Câu nói |
|---|---|---|
| 0:00–0:30 | Mở README, chỉ vào ba điểm nhấn | "Em làm backend bán đồ điện tử. Điểm em đầu tư sâu nhất là tồn kho dưới tải đồng thời — em sẽ demo trực tiếp." |
| 0:30–1:30 | Swagger: `GET /products` với lọc spec | "350 sản phẩm thật. Bộ lọc theo thông số chạy trên JSONB có GIN index — p95 42ms, trước khi thêm index là 380ms." |
| 1:30–2:30 | Chạy `demo.sh`: đăng nhập → giỏ → checkout | "Bước checkout **giữ hàng 10 phút**. Chú ý `availableQuantity` giảm ngay, còn `stockQuantity` chưa đổi — hàng mới bị giữ, chưa bị trừ." |
| 2:30–3:30 | **Chạy `flashsale-race.sh`** | "500 request đồng thời vào 50 suất." Chờ kết quả in ra: `50 thành công / 312 sold out / 138 rate-limited`. Rồi query SQL: `sold_quantity = 50`. "Khớp tuyệt đối." |
| 3:30–4:15 | Mở Grafana chỉ đỉnh traffic; mở file test concurrency | "Đây là test tự động chạy trong CI. Và đây là commit test này **fail** trước khi em thêm khóa — oversell 190 đơn." |
| 4:15–5:00 | Mở một ADR (0003 hoặc 0004) | "Mỗi quyết định lớn em ghi lại lý do và đánh đổi. Ví dụ vì sao đơn thường dùng pessimistic lock nhưng flash sale phải dùng Redis." |

**Phút 2:30–3:30 là toàn bộ giá trị của buổi demo.** Nếu chỉ còn 90 giây, chạy đúng đoạn đó.

Chuẩn bị sẵn `flashsale-race.sh` reset dữ liệu về trạng thái đầu trước khi chạy, để demo lại được nhiều lần không lỗi.

---

## 4. Ba câu chuyện kỹ thuật (dàn ý STAR)

### Câu chuyện 1 — Chống oversell

- **Bối cảnh:** hàng giá trị cao, tồn kho thấp, nhiều người mua cùng lúc.
- **Vấn đề:** hai request cùng đọc tồn = 1, cả hai cùng thấy đủ hàng, cả hai cùng trừ → tồn kho âm.
- **Đã thử:** ban đầu chỉ `@Transactional` — viết test 200 thread thì fail ngay, bán 200 đơn trên 10 sản phẩm. Transaction đảm bảo *atomic*, không đảm bảo *isolation* ở mức mình cần.
- **Giải pháp:** `SELECT ... FOR UPDATE` với `ORDER BY id` để tránh deadlock khi hai đơn khóa cùng bộ variant theo thứ tự ngược nhau. Thêm `CHECK (stock_quantity >= 0)` ở DB làm chốt chặn cuối.
- **Kết quả:** test 200 thread cho đúng 10 đơn. Có trong CI.
- **Đánh đổi:** pessimistic lock giảm throughput khi tranh chấp cao — nên flash sale dùng cách khác (câu chuyện 3).

### Câu chuyện 2 — Reservation và chuyện Redis chết

- **Bối cảnh:** khách vào trang thanh toán, đang nhập địa chỉ thì hàng bị người khác mua mất.
- **Giải pháp:** bản ghi `inventory_reservations` giữ hàng 10 phút. `available = stock − reserved`.
- **Quyết định khó:** giữ trạng thái ở Redis (TTL tự hết hạn, nhanh) hay ở DB (chậm hơn, phải có job quét)? Chọn **DB là nguồn sự thật, Redis là đường nhanh**.
- **Lý do:** Redis keyspace notification là *at-most-once* — mất một event là hàng bị giữ vĩnh viễn, không ai nhả. Redis restart là mất sạch reservation. Job quét DB mỗi 30 giây chậm hơn nhưng không bao giờ mất.
- **Kiểm chứng:** integration test tắt hẳn container Redis giữa chừng, khẳng định reservation vẫn được nhả đúng.
- **Đánh đổi:** độ trễ nhả hàng tối đa 30 giây thay vì tức thì. Chấp nhận được — hàng nhả muộn 30 giây không hại ai, hàng kẹt vĩnh viễn thì có.

### Câu chuyện 3 — Flash sale

- **Bối cảnh:** vài nghìn request/giây đổ vào **cùng một dòng** dữ liệu trong vài giây đầu.
- **Vì sao khóa DB không dùng được:** mọi request xếp hàng chờ cùng một row lock; connection pool cạn; các API khác cũng chết theo.
- **Vì sao `synchronized` vô dụng:** chỉ khóa trong một JVM, deploy 2 instance là hỏng.
- **Giải pháp:** Lua script trên Redis kiểm tồn + kiểm giới hạn/user + trừ tồn trong **một** lệnh nguyên tử; đồng bộ về DB qua outbox. Rate limit Bucket4j + Redis chặn bot, trả `429` kèm `Retry-After`. Nạp sẵn cache trước giờ mở bán để tránh cache stampede.
- **Vì sao Lua chứ không phải `DECR`:** `DECR` → kiểm tra âm → `INCR` bù là ba lệnh, có khe hở cho request khác chen vào.
- **Kết quả:** 500 request / 50 suất → đúng 50 đơn, 0 lỗi 5xx.
- **Đánh đổi thừa nhận:** nhất quán cuối (eventual consistency) giữa Redis và DB, có job đối soát cuối phiên. Và nếu Redis chết thì **fail-closed** — từ chối bán, vì không đảm bảo được thì không bán còn hơn bán quá.

---

## 5. Ngân hàng câu hỏi phỏng vấn

### Về concurrency (chắc chắn được hỏi)

| Câu hỏi | Ý chính |
|---|---|
| Optimistic khác pessimistic ở đâu? | Optimistic: không khóa, so `version` lúc ghi, thua thì retry/báo lỗi — hợp khi tranh chấp thấp. Pessimistic: khóa dòng lúc đọc, ai đến sau chờ — hợp khi tranh chấp cao và không muốn retry |
| Project bạn dùng cái nào? | **Cả ba**, mỗi chỗ một lý do: pessimistic cho đặt hàng, optimistic cho đổi trạng thái đơn, Redis atomic cho flash sale |
| Sao không `synchronized`? | Chỉ khóa trong một JVM. Nhiều instance là vô dụng. Khóa phải nằm ở tầng dùng chung — DB hoặc Redis |
| Deadlock xảy ra khi nào trong project bạn? | Hai đơn mua cùng bộ variant theo thứ tự ngược nhau. Chặn bằng `ORDER BY id` trước khi khóa |
| `@Transactional` có chống được race condition không? | **Không.** Nó đảm bảo all-or-nothing, không đảm bảo hai transaction không cùng đọc một giá trị cũ. Cần isolation level cao hơn hoặc khóa tường minh |
| Isolation level mặc định của PostgreSQL? | `READ COMMITTED`. Nó chặn dirty read nhưng không chặn non-repeatable read — chính là kẽ hở gây oversell |
| `SERIALIZABLE` giải quyết được không? | Được, nhưng phải xử lý `SerializationFailure` và retry, và throughput giảm mạnh. Khóa dòng có chủ đích rẻ hơn |

### Về JPA/Hibernate

| Câu hỏi | Ý chính |
|---|---|
| N+1 là gì, ở đâu trong project bạn? | Nêu 4 chỗ ở `01-domain-v2.md` mục 7, và nói thêm **có test hồi quy đếm số query** |
| `LAZY` vs `EAGER`? | Mặc định LAZY hết; cần thì `JOIN FETCH`/`@EntityGraph` tại chỗ. EAGER là quyết định toàn cục cho một vấn đề cục bộ |
| `LazyInitializationException` xử lý sao? | Không phải bằng cách bật EAGER hay `open-in-view`. Nạp đủ dữ liệu trong transaction và map sang DTO trước khi ra khỏi service |
| `@Transactional` đặt ở đâu, vì sao? | Tầng service. Đó là ranh giới nghiệp vụ. Đặt ở controller là trộn tầng và kéo dài transaction qua cả serialize response |
| `@Transactional` gọi nội bộ có chạy không? | **Không** — proxy bị bỏ qua khi gọi method cùng class. Bẫy kinh điển |
| Vì sao `BigDecimal` chứ không `double`? | Sai số nhị phân khi cộng dồn tiền. Và nhớ: so sánh bằng `compareTo`, không `equals` |

### Về thiết kế

| Câu hỏi | Ý chính |
|---|---|
| Vì sao JSONB chứ không EAV? | EAV mọi truy vấn lọc thành chuỗi self-join, khó index. JSONB + GIN cân bằng tốt. **Nhược điểm phải thừa nhận:** không ràng buộc kiểu ở DB, phải validate ở app |
| Vì sao snapshot giá trong `order_items`? | Đơn hàng là bản ghi lịch sử bất biến. Và snapshot cả tên + SKU, để in được hóa đơn kể cả khi sản phẩm bị xóa |
| Vì sao soft delete? | Xóa cứng sản phẩm đã có trong đơn cũ sẽ vi phạm khóa ngoại hoặc phá lịch sử đơn hàng |
| Idempotency-Key hoạt động thế nào? | Client sinh UUID; server chốt bằng `UNIQUE` ở DB (Redis chỉ là lớp chặn nhanh); cùng key khác payload thì trả `409`, không trả kết quả cũ |
| Vì sao outbox thay vì gọi thẳng? | Gửi mail trong transaction kéo dài thời gian giữ khóa; rollback sau khi gửi thì khách nhận mail về đơn không tồn tại. Outbox ghi event cùng transaction, worker gửi sau khi đã commit |
| Nếu scale, bạn tách service nào trước? | `inventory` — nó có ràng buộc đồng thời riêng và mô hình tải khác hẳn phần còn lại |

### Câu hỏi về chế độ hỏng (ít người chuẩn bị — cơ hội của bạn)

| Câu hỏi | Ý chính |
|---|---|
| Redis chết thì sao? | Bảng ở `04-error-catalog.md` mục 9. **Rate limit fail-open, flash sale fail-closed** — cùng hạ tầng chết, hai ứng xử ngược nhau, vì hậu quả của việc sai khác nhau |
| Server crash giữa lúc đang giữ hàng? | Reservation nằm trong DB nên còn nguyên; job quét vẫn nhả đúng khi service khởi động lại |
| Webhook thanh toán gửi trùng? | `UNIQUE(transaction_ref)`, và luôn đối chiếu số tiền trước khi đánh dấu `PAID` |
| Counter `reserved_quantity` lệch thì sao? | Job đối soát định kỳ so với `SUM` trên bảng reservation, log cảnh báo |
| Traffic tăng 100 lần thì nghẽn ở đâu trước? | Nêu bảng nút thắt ở `06-nfr-va-load-test.md` mục 4, và nhấn: **rút ngắn transaction trước, tăng connection pool sau** |

### Câu hỏi mềm

| Câu hỏi | Ý chính |
|---|---|
| Phần khó nhất? | Reservation — không phải vì code khó, mà vì quyết định "nguồn sự thật nằm ở đâu" ảnh hưởng tới mọi thứ phía sau |
| Nếu làm lại, bạn đổi gì? | Thật lòng: viết concurrency test **trước** khi viết logic đặt hàng. Em viết sau và phải sửa lại thiết kế service |
| Bạn học Java bao lâu rồi? | Nói thật, và nối sang lợi thế: đã làm backend bằng Python (FastAPI, PostgreSQL, Docker, pytest) nên khái niệm chuyển thẳng sang; phần phải học là hệ sinh thái Spring, không phải học lại backend |
| Có gì bạn biết là chưa tốt? | Chọn thật một điểm: ví dụ chưa có audit log cho hành động admin, hoặc phần đối soát Redis–DB mới ở mức log chứ chưa tự sửa |

---

## 6. Khi không biết câu trả lời

Đừng đoán bừa và đừng im. Công thức:

> "Cái này em chưa làm trong project. Em hiểu vấn đề là ... — em đoán hướng giải sẽ là ..., nhưng em chưa kiểm chứng nên không chắc."

Nêu đúng vấn đề rồi thừa nhận giới hạn được đánh giá cao hơn nhiều so với trả lời trôi chảy mà sai. Với fresher, thứ được đo là **cách suy nghĩ**, không phải lượng kiến thức.

---

## 7. Checklist trước buổi phỏng vấn

- [ ] Link deploy còn sống (kiểm lại buổi sáng hôm phỏng vấn — Render ngủ instance free)
- [ ] `docker compose up` chạy sạch trên máy vừa clone mới
- [ ] `demo.sh` và `flashsale-race.sh` chạy trơn, có reset dữ liệu
- [ ] Đọc lại 8 ADR
- [ ] Tự nói to ba câu chuyện ở mục 4, mỗi câu 2 phút, không nhìn giấy
- [ ] Chuẩn bị 3 câu hỏi ngược lại cho nhà tuyển dụng (về codebase, quy trình review, người mới được onboard thế nào)
- [ ] Mở sẵn tab: README · Swagger · file test concurrency · Grafana
