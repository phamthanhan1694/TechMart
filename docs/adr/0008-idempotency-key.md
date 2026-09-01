# ADR-0008: `Idempotency-Key` ở tầng API, chốt chặn bằng ràng buộc DB

- **Trạng thái:** Chấp nhận
- **Ngày:** 2026-09-01
- **Liên quan:** ADR-0004, `03-api-contract.md` §1

## Bối cảnh

Khách bấm "Đặt hàng" hai lần vì mạng lag, hoặc client tự retry khi timeout. Nếu không xử lý thì một lần mua thành hai đơn — và với thanh toán thì thành trừ tiền hai lần.

Đây không phải trường hợp hiếm: retry là hành vi mặc định của gần như mọi HTTP client, và cổng thanh toán thật **sẽ** gửi lại callback khi không nhận được `200`.

## Các phương án đã cân nhắc

| Phương án | Ưu | Nhược |
|---|---|---|
| Chống trùng ở client (khóa nút bấm) | Không cần gì ở server | Không kiểm soát được client; retry ở tầng mạng vẫn lọt |
| Dò trùng theo nội dung (cùng user + cùng giỏ + trong 5 phút) | Client không phải làm gì | Đoán mò. Khách muốn mua hai lần thật thì bị chặn oan |
| **`Idempotency-Key` do client sinh** | Rõ ràng, khách chủ động, chuẩn công nghiệp (Stripe) | Client phải sinh UUID cho mỗi thao tác |
| Chỉ cache key trên Redis | Nhanh | Redis mất dữ liệu là mất bảo vệ — đúng lúc cần nhất |

## Quyết định

Header `Idempotency-Key` **bắt buộc** cho `POST /orders`, `POST /payments`, `POST /flash-sales/items/{id}/purchase`. Thiếu thì trả `400 IDEMPOTENCY_KEY_REQUIRED` chứ không lặng lẽ bỏ qua.

Hai lớp, và thứ tự quan trọng:

1. **Đường nhanh:** tra cứu theo key, có rồi thì trả lại kết quả cũ.
2. **Chốt chặn thật:** ràng buộc `UNIQUE(orders.idempotency_key)` và `UNIQUE(payments.idempotency_key)` ở DB. Hai request song song cùng key thì một cái vi phạm ràng buộc, bắt `DataIntegrityViolationException` và đọc lại kết quả của cái thắng.

Lớp 1 không đủ: hai request đến *cùng lúc* thì cả hai đều thấy "chưa có". Chỉ ràng buộc DB mới phân xử được. Đây cũng là triết lý xuyên suốt dự án — hạ tầng nhanh có thể mất, nguồn sự thật thì không.

Cùng key nhưng **payload khác** trả `409 IDEMPOTENCY_KEY_CONFLICT` chứ không trả kết quả cũ: client đang định làm việc khác, trả kết quả cũ là che giấu lỗi của họ.

## Đánh đổi chấp nhận

- Client bắt buộc phải sinh UUID. Đã ghi rõ trong mô tả Swagger.
- Key lưu vĩnh viễn trong bảng `orders`/`payments`, không hết hạn. Đơn giản hơn là dọn định kỳ, và dung lượng không đáng kể.
- Bảng `idempotency_records` đã tạo trong schema nhưng **chưa dùng** — hiện chốt chặn nằm trực tiếp trên `orders`/`payments`. Cần khi có endpoint idempotent không gắn với một bảng nghiệp vụ nào.

## Khi nào nên đổi ý

- Khi cần lưu và phát lại **nguyên văn response** (kể cả header, status code) cho mọi endpoint — lúc đó dùng bảng `idempotency_records` chung thay vì cột trên từng bảng.
- Khi có endpoint idempotent không ghi vào bảng nào — cột `UNIQUE` không còn chỗ để đặt.

## Kiểm chứng

- `initiate_sameIdempotencyKey_returnsSamePayment`
- `placeOrder_sameIdempotencyKeyTwice_createsOneOrder`
- `webhook_deliveredTwice_isIdempotent`
- `demo.sh` bước 6 chạy thật hai lần và so mã đơn
