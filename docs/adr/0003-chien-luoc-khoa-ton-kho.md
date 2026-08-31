# ADR-0003: Pessimistic lock cho đơn thường, Redis atomic cho flash sale

- **Trạng thái:** Chấp nhận
- **Ngày:** 2026-09-01
- **Liên quan:** ADR-0004, `02-luong-nghiep-vu.md` §3 và §4, `05-test-plan.md` §3.1

## Bối cảnh

Hai request cùng đọc `stock_quantity = 1`, cả hai thấy đủ hàng, cả hai trừ kho → bán quá tồn. PostgreSQL mặc định `READ COMMITTED`, chặn dirty read nhưng **không** chặn hai transaction cùng đọc một giá trị cũ — nên `@Transactional` một mình không đủ.

Hệ thống có hai mô hình tải rất khác nhau:
- **Đặt hàng thường:** tranh chấp thấp, mỗi variant hiếm khi có hai người mua cùng lúc, nhưng phải đúng tuyệt đối.
- **Flash sale:** vài nghìn request/giây đổ vào **cùng một dòng** trong vài giây đầu.

Một chiến lược khóa duy nhất không phục vụ tốt cả hai.

## Các phương án đã cân nhắc

| Phương án | Ưu | Nhược |
|---|---|---|
| Optimistic (`@Version` + retry) | Không giữ khóa, throughput tốt khi ít đụng | Tranh chấp cao → bão retry, tình huống xấu tệ hơn cả pessimistic |
| Pessimistic (`SELECT … FOR UPDATE`) | Đúng tuyệt đối, không cần retry, code đơn giản | Request xếp hàng chờ cùng row lock; tải cao thì cạn connection pool và kéo sập cả API khác |
| `SERIALIZABLE` | DB tự lo | Phải xử lý `SerializationFailure` + retry, throughput giảm mạnh |
| Redis atomic (Lua) | Chịu được vài nghìn req/s trên một khóa | Nhất quán cuối với DB, phụ thuộc Redis sống |
| `synchronized` trong Java | — | **Loại ngay**: chỉ khóa trong một JVM, vô dụng khi chạy nhiều instance |

## Quyết định

**Dùng cả ba, mỗi chỗ theo mô hình tải của nó:**

1. **Đặt hàng thường → pessimistic** `@Lock(PESSIMISTIC_WRITE)`, khóa các variant theo `ORDER BY id` (chống deadlock khi hai đơn khóa cùng bộ variant theo thứ tự ngược nhau). Tranh chấp thấp nên chi phí chờ gần như bằng không, đổi lại code không cần retry.
2. **Đổi trạng thái đơn, trừ lượt coupon → optimistic** `@Version`. Đụng độ hiếm và khi đụng thì trả `409` là **hành vi đúng về nghiệp vụ**, không phải lỗi cần retry.
3. **Flash sale → Redis Lua script**, đồng bộ về DB qua outbox. Lua chứ không phải `DECR`: `DECR` → kiểm âm → `INCR` bù là ba lệnh, có khe hở cho request khác chen vào.

Thêm **chốt chặn cuối ở DB**: `CHECK (stock_quantity >= 0)` và `CHECK (reserved_quantity <= stock_quantity)`. Nếu logic khóa sai, DB ném lỗi thay vì để tồn kho âm.

## Đánh đổi chấp nhận

- Ba cơ chế khác nhau trong một codebase → chi phí nhận thức cao hơn, phải tài liệu hóa rõ (chính là ADR này).
- Pessimistic lock giới hạn throughput của luồng đặt hàng. Chấp nhận vì quy mô hiện tại còn cách ngưỡng đó rất xa.
- Flash sale nhất quán cuối giữa Redis và DB → cần job đối soát cuối phiên.
- Nếu Redis chết, flash sale **fail-closed** (`503`). Từ chối bán an toàn hơn bán quá.

## Khi nào nên đổi ý

- Nếu đặt hàng thường bắt đầu có tranh chấp thật (nhiều người mua cùng variant), thời gian chờ khóa sẽ tăng → chuyển sang trừ tồn qua Redis giống flash sale.
- Nếu triển khai nhiều instance app và đo thấy connection pool cạn khi tải cao → dấu hiệu pessimistic lock đã tới giới hạn.
- Nếu tách `inventory` thành service riêng, toàn bộ chiến lược này phải xem lại — khóa khi đó là khóa phân tán.

## Kiểm chứng

- `placeOrder_concurrent_shouldNeverOversell()` — 200 thread mua variant tồn 10 → đúng 10 đơn, tồn kho về 0. Chạy trong CI.
- Commit `<hash>` cho thấy test này **fail** trước khi thêm khóa (200 đơn thành công, tồn kho −190); commit `<hash>` cho thấy pass sau khi thêm.
- k6: 500 request đồng thời / 50 suất flash sale → đúng 50 đơn, `sold_quantity` trong DB khớp tuyệt đối, 0 lỗi 5xx. Trước khi dùng Lua: oversell `<N>` đơn.

> *(Điền hash commit và số liệu sau khi đo ở tuần 5.)*
