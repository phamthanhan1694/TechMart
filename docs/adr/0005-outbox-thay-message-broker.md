# ADR-0005: Outbox pattern thay cho message broker

- **Trạng thái:** Chấp nhận
- **Ngày:** 2026-09-01
- **Liên quan:** ADR-0001, `02-luong-nghiep-vu.md` §7

## Bối cảnh

Sau khi đặt hàng cần gửi email xác nhận. Gọi thẳng `mailService.send()` trong transaction đặt hàng có hai vấn đề:

1. Gửi mail chậm và có thể lỗi — kéo dài transaction, giữ khóa dòng `product_variants` lâu hơn cần thiết. Đúng chỗ mà mọi request đang xếp hàng chờ.
2. Nếu transaction rollback **sau** khi mail đã gửi, khách nhận mail về một đơn hàng không tồn tại.

## Các phương án đã cân nhắc

| Phương án | Ưu | Nhược |
|---|---|---|
| Gọi thẳng trong transaction | Đơn giản nhất | Cả hai vấn đề trên |
| `@TransactionalEventListener(AFTER_COMMIT)` đơn thuần | Giải được vấn đề 2, không thêm hạ tầng | Event sống trong bộ nhớ: app chết sau commit là **mất event**, không ai biết |
| **Outbox table + worker** | Event ghi cùng transaction nên không thể mất; retry được; đếm được số event ứ | Chậm hơn (chu kỳ worker), thêm một bảng |
| RabbitMQ / Kafka | Đúng công cụ khi đã có nhiều consumer | Thêm một container, một giao thức, một thứ phải vận hành — trong khi vẫn *không* giải được vấn đề "ghi DB và publish message phải cùng nguyên tử" (vẫn cần outbox) |

## Quyết định

Ghi `outbox_events` trong **cùng transaction** với thay đổi nghiệp vụ. Một worker `@Scheduled` đọc lô 100 event bằng `SELECT ... FOR UPDATE SKIP LOCKED`, xử lý, đánh dấu `PROCESSED`, retry tối đa 5 lần rồi chuyển `FAILED`.

`SKIP LOCKED` là chi tiết đáng nói: nó cho phép chạy nhiều instance worker song song mà không giành nhau cùng một dòng — tức là phần xử lý bất đồng bộ scale ngang được mà vẫn không cần broker.

Điểm mấu chốt: **message broker không thay thế được outbox.** Kể cả có RabbitMQ, vẫn cần outbox để đảm bảo "ghi đơn hàng" và "publish sự kiện" là nguyên tử. Broker chỉ thay phần *vận chuyển*, mà phần đó ở quy mô này chưa cần.

## Đánh đổi chấp nhận

- **Giao hàng at-least-once.** Worker chết sau khi gửi nhưng trước khi commit thì mail gửi trùng. Chấp nhận được với email; nếu là trừ tiền thì phía nhận phải có khóa idempotent riêng.
- Độ trễ bằng chu kỳ worker (5 giây), không tức thì.
- Bảng outbox lớn dần — cần job dọn event `PROCESSED` cũ (chưa làm).

## Khi nào nên đổi ý

- Khi có **consumer thứ hai** cần cùng một sự kiện (ví dụ hệ thống phân tích, dịch vụ vận chuyển) — lúc đó broker mới đáng: outbox worker chỉ còn việc đẩy sang broker.
- Khi độ trễ 5 giây không chấp nhận được.
- Khi số event/giây vượt khả năng của một bảng Postgres.

## Kiểm chứng

Gauge `techmart_outbox_events_pending` trên Grafana — ứ lại nghĩa là worker chết hoặc đang lỗi. Đây là metric vận hành, không phải test.
