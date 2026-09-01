# ADR-0010: Flash sale — Redis là *cửa*, DB vẫn là *sổ cái*

- **Trạng thái:** Chấp nhận
- **Ngày:** 2026-09-01
- **Liên quan:** ADR-0003, ADR-0004, ADR-0009

## Bối cảnh

Mở bán giới hạn có mô hình tải khác hẳn phần còn lại của hệ thống: vài nghìn request/giây đổ vào **cùng một dòng** dữ liệu trong vài giây đầu.

Khóa bi quan — thứ dùng cho đơn hàng thường (ADR-0003) — không chịu nổi ở đây: mọi request xếp hàng chờ cùng một row lock, mỗi request giữ một connection trong lúc chờ, connection pool cạn, và **các API khác chết theo**. Sự cố lan ra ngoài phạm vi tính năng gây ra nó.

## Các phương án đã cân nhắc

| Phương án | Ưu | Nhược |
|---|---|---|
| Khóa bi quan như đơn thường | Nhất quán tuyệt đối | Cạn connection pool, kéo sập cả ứng dụng |
| Hàng đợi xử lý tuần tự (RabbitMQ) | Không dồn vào DB | Khách không biết mình mua được hay không cho tới khi worker xử lý xong — trải nghiệm tệ ở đúng lúc căng thẳng nhất |
| Redis `DECR` rồi kiểm tra | Đơn giản | `DECR` → thấy âm → `INCR` bù là **ba lệnh không nguyên tử**, có khe hở cho request khác đọc phải tồn kho âm |
| **Redis Lua script** | Kiểm tồn + kiểm giới hạn/user + trừ trong MỘT lệnh nguyên tử; trả kết quả ngay | Nhất quán cuối với DB |

## Quyết định

Kiến trúc hai tầng:

1. **Cửa Redis** — Lua script (`redis/flashsale-purchase.lua`) quyết định ai được mua. Chạy trên Redis như một lệnh duy nhất, không có khe hở.
2. **Sổ cái DB** — chỉ người **đã thắng** ở cửa mới mở transaction ghi đơn và trừ tồn kho thật. Số transaction chạm vào dòng `flash_sale_items` vì vậy bị chặn trên bởi `sale_quantity` — vài chục, không phải vài nghìn.

Ghi DB thất bại thì **trả lại suất** cho Redis, nếu không suất đó bốc hơi và cửa hàng bán hụt.

Bước gọi Redis nằm **ngoài** transaction, có chủ đích: nếu gọi trong transaction thì hàng nghìn request giữ connection DB trong lúc chờ Redis trả lời, và pool cạn trước khi kịp từ chối ai. Yêu cầu "ngoài transaction rồi mới vào transaction" là lý do phải tách `FlashSaleOrderWriter` thành bean riêng — Spring AOP qua proxy nên gọi nội bộ không kích hoạt được `@Transactional`.

**Fail-closed khi Redis chết:** không có bộ đếm thì trả `503`, từ chối bán. Ngược với rate limit (fail-open, ADR-0009) — cùng một hạ tầng hỏng, hai ứng xử ngược nhau, vì hậu quả của việc sai khác nhau: chặn nhầm người dùng chỉ gây phiền, còn bán quá số suất thì phải đền bù thật.

Endpoint `/warmup` nạp sẵn tồn kho trước giờ mở bán. Nếu để request đầu tiên tự nạp thì đúng giây mở bán sẽ có hàng nghìn request cùng nạp — **cache stampede**.

## Đánh đổi chấp nhận

- **Nhất quán cuối** giữa Redis và DB trong lúc phiên chạy. Có endpoint `/reconcile` đối soát cuối phiên và ghi cảnh báo khi lệch.
- Phụ thuộc Redis sống. Đã chọn fail-closed nên hỏng thì mất tính năng, không mất tính đúng đắn.
- Logic tồn kho nằm ở hai nơi (Lua và Java) — phải đọc cả hai mới hiểu hết.

## Khi nào nên đổi ý

- Khi số suất mở bán lớn tới mức ngay cả "chỉ người thắng chạm DB" cũng là vài nghìn transaction — lúc đó cần hàng đợi ghi bất đồng bộ.
- Khi cần nhất quán mạnh tuyệt đối giữa Redis và DB (ví dụ hàng có định danh serial phải gán ngay) — quay lại khóa DB và chấp nhận throughput thấp hơn.
- Khi chạy Redis Cluster: Lua script yêu cầu mọi key trong cùng một slot, phải dùng hash tag `{itemId}`.

## Kiểm chứng

- `flashSalePurchase_underThunderingHerd_neverOversells` — 500 thread / 50 suất → đúng 50 đơn, `sold_quantity` DB khớp, bộ đếm Redis về 0, 0 lỗi 5xx
- `purchase_beyondPerUserLimit_isRejected`
- `purchase_withoutWarmUp_failsClosed` — chứng minh trực tiếp quyết định fail-closed
- `flashsale-race.sh` chạy thật: 75 người tranh 48 suất → đúng 48 người mua được
