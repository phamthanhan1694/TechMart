# ADR-0004: Reservation — DB là nguồn sự thật, Redis là đường nhanh

- **Trạng thái:** Chấp nhận
- **Ngày:** 2026-09-01
- **Liên quan:** ADR-0003, ADR-0005, `02-luong-nghiep-vu.md` §2, `04-error-catalog.md` §9

## Bối cảnh

Khách vào bước thanh toán, đang nhập địa chỉ thì hàng bị người khác mua mất → báo "hết hàng" giữa chừng, trải nghiệm tệ và mất đơn. Cần **giữ hàng tạm thời** trong 10 phút, tự nhả nếu không thanh toán.

Câu hỏi thiết kế thật sự không phải "làm reservation thế nào" mà là **trạng thái giữ hàng sống ở đâu** — vì nó quyết định điều gì xảy ra khi hạ tầng hỏng.

## Các phương án đã cân nhắc

| Phương án | Ưu | Nhược |
|---|---|---|
| Redis key có TTL, nhả bằng keyspace notification | Hết hạn tức thì, không cần job, rất nhanh | Notification là **at-most-once** — mất một event là hàng bị giữ **vĩnh viễn**. Redis restart mất sạch reservation. Không audit được ai giữ gì |
| Bảng DB + job `@Scheduled` quét hết hạn | Bền vững, audit được, sống sót qua restart | Nhả hàng trễ tối đa bằng chu kỳ job; job phải có index và chạy theo lô |
| Chỉ DB, không Redis | Đơn giản nhất | Mỗi lượt xem sản phẩm phải `SUM` bảng reservation → trang danh sách chết dưới tải |
| Hàng đợi có độ trễ (delayed queue) | Nhả đúng giờ | Thêm một hạ tầng nữa chỉ để giải một việc |

## Quyết định

**Bảng `inventory_reservations` là nguồn sự thật.** Redis chỉ là lớp tăng tốc, mất được.

Cụ thể:
- Giữ hàng = một transaction ghi `inventory_reservations` (`status = HELD`, `expires_at`) **và** tăng `product_variants.reserved_quantity` — bộ đếm phi chuẩn hóa để `available = stock − reserved` đọc được trong một truy vấn.
- Redis `SETEX resv:{id}` là best-effort, dùng để kiểm tra nhanh, **không** dùng để quyết định đúng/sai.
- Job `@Scheduled` mỗi 30 giây quét `WHERE status = 'HELD' AND expires_at < now() LIMIT 500`, có partial index trên `expires_at`.
- Nhả hàng viết dưới dạng `UPDATE … WHERE status = 'HELD'` (điều kiện nằm trong câu lệnh, không đọc-rồi-ghi) nên **idempotent**, job chạy chồng vẫn đúng.

## Đánh đổi chấp nhận

- Nhả hàng trễ tối đa 30 giây thay vì tức thì. **Hàng nhả muộn 30 giây không hại ai; hàng kẹt vĩnh viễn thì có** — đây là toàn bộ lý do của quyết định.
- Hai chỗ ghi (`reserved_quantity` và bảng reservation) phải luôn đồng bộ. Chặn bằng: cùng transaction, chỉ sửa qua một service duy nhất, và job đối soát định kỳ so counter với `SUM` rồi log lệch.
- Job quét là tải nền thường trực trên DB. Nhỏ, nhưng phải có index — thiếu index thì nó full-scan mỗi 30 giây.

## Khi nào nên đổi ý

- Nếu số reservation đồng thời lên tới hàng trăm nghìn, job quét theo lô sẽ không theo kịp → chuyển sang phân vùng theo `expires_at` hoặc dùng delayed queue thật.
- Nếu yêu cầu nghiệp vụ đổi thành "nhả tức thì đúng giây thứ 600", chu kỳ 30 giây không còn chấp nhận được.
- Nếu tách `inventory` thành service riêng có kho dữ liệu riêng, mô hình này giữ nguyên nhưng `reserved_quantity` phải chuyển thành API thay vì đọc cột.

## Kiểm chứng

- `expiredReservation_isReleasedByScheduledJob()` — dùng `Clock` cố định đẩy thời gian lên 11 phút, không `Thread.sleep`.
- `redisDown_reservationStillWorks()` — **tắt hẳn container Redis giữa test**, khẳng định reservation vẫn được nhả đúng và `available` vẫn tính đúng. Đây là test chứng minh trực tiếp cho quyết định này.
- `reservationExpiry_concurrentWithCheckout_shouldNotDoubleRelease()` — job hết hạn chạy đúng lúc user commit; `reserved_quantity` không âm, không trừ hai lần.
