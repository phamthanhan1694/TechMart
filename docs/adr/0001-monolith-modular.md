# ADR-0001: Monolith module hóa theo domain, không tách microservices

- **Trạng thái:** Chấp nhận
- **Ngày:** 2026-09-01
- **Liên quan:** `00-pham-vi-va-ke-hoach.md` §2

## Bối cảnh

Dự án làm trong ~5 tuần bởi một người, chạy trên một máy, không có traffic thật. Microservices là thứ được nhắc nhiều nhất trong các portfolio e-commerce, nên câu hỏi không phải "có làm được không" mà "làm thì được gì".

## Các phương án đã cân nhắc

| Phương án | Ưu | Nhược |
|---|---|---|
| Monolith một cục, package theo tầng (`controller`/`service`/`repository`) | Nhanh nhất để bắt đầu | Sau vài nghìn dòng thì không còn thấy ranh giới nghiệp vụ ở đâu |
| Monolith **module hóa theo domain** | Ranh giới nghiệp vụ rõ, tách service sau này chỉ là việc di chuyển package | Phải tự kỷ luật, không có gì cưỡng chế ranh giới |
| Microservices thật | Đúng "từ khóa" trên CV | Thêm service discovery, distributed transaction, tracing, và 3–4 tuần hạ tầng — trong khi bài toán lõi (chống oversell) *khó hơn* khi phân tán |

## Quyết định

Monolith, package theo **domain** chứ không theo tầng: `identity`, `catalog`, `cart`, `inventory`, `order`, `payment`, `promotion`, `flashsale`, `admin`. Mỗi domain tự chứa `domain/`, `repository/`, `service/`, `web/`, `dto/`.

Quyết định này giữ cho câu hỏi phỏng vấn "nếu scale thì tách gì trước" có câu trả lời cụ thể thay vì chung chung: tách `inventory` trước, vì nó là domain duy nhất có ràng buộc đồng thời riêng và mô hình tải khác hẳn phần còn lại.

## Đánh đổi chấp nhận

- Không có gì cưỡng chế ranh giới — `OrderService` vẫn `import` thẳng được `CouponService`. Chấp nhận, đổi lại không phải dựng module system.
- Không thể scale riêng từng phần. Ở quy mô này chưa cần.
- Mất "microservices" như một từ khóa trên CV. Đổi lại có một câu trả lời thật cho câu hỏi *khi nào* nên tách.

## Khi nào nên đổi ý

- Khi một domain có mô hình tải khác hẳn và bắt đầu ảnh hưởng phần còn lại — cụ thể là `inventory` dưới flash sale, nếu khóa bi quan bắt đầu làm cạn connection pool của cả ứng dụng.
- Khi có nhiều hơn một đội cùng sửa một codebase và merge conflict trở thành chi phí thật.
- **Không** đổi ý chỉ vì số dòng code tăng.

## Kiểm chứng

Không có test cho quyết định kiến trúc. Dấu hiệu nó còn đúng: thêm module `flashsale` và `payment` ở tuần 4 không phải sửa gì trong `catalog` hay `identity`.
