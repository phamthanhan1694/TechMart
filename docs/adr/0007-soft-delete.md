# ADR-0007: Xóa mềm cho sản phẩm và biến thể

- **Trạng thái:** Chấp nhận
- **Ngày:** 2026-09-01
- **Liên quan:** `01-domain-v2.md` §1

## Bối cảnh

Admin cần gỡ sản phẩm ngừng kinh doanh khỏi cửa hàng. Nhưng sản phẩm đó đã nằm trong `order_items` của những đơn hàng cũ — mà đơn hàng là **bản ghi lịch sử bất biến**, phải in được hóa đơn sau nhiều năm.

Đây là lỗi thiết kế trong bản đặc tả v1 ban đầu: `DELETE /products/{id}` sẽ vi phạm khóa ngoại ngay lần đầu có ai xóa một sản phẩm đã bán.

## Các phương án đã cân nhắc

| Phương án | Ưu | Nhược |
|---|---|---|
| Xóa cứng | Sạch sẽ | Vi phạm khóa ngoại, hoặc `ON DELETE CASCADE` xóa luôn lịch sử đơn hàng |
| Xóa cứng + `ON DELETE SET NULL` trên `order_items.variant_id` | Giữ được đơn | Mất khả năng thống kê theo sản phẩm; đơn cũ mất liên kết |
| **Xóa mềm** (`deleted_at`) | Đơn cũ nguyên vẹn, khôi phục được, thống kê vẫn chạy | Mọi truy vấn public phải nhớ lọc — dễ quên |
| Bảng lưu trữ riêng | Bảng chính gọn | Thêm một bảng và một luồng di chuyển dữ liệu |

## Quyết định

Xóa mềm bằng cột `deleted_at` trên `products` và `product_variants`. Xóa một sản phẩm cũng xóa mềm toàn bộ biến thể của nó.

Dùng **`@SQLRestriction("deleted_at IS NULL")`** của Hibernate để tự động thêm điều kiện vào mọi truy vấn JPA — vì "phải nhớ lọc" là loại yêu cầu con người luôn quên. Index cũng là partial index (`WHERE deleted_at IS NULL`) nên nhỏ hơn và nhanh hơn.

Bổ sung: `order_items` **snapshot** `product_name`, `variant_name`, `sku`, `unit_price` tại thời điểm mua. Nhờ vậy đơn cũ đọc được ngay cả khi sản phẩm đã xóa mềm hoặc đổi tên — không bao giờ đọc ngược sang `products` để hiển thị đơn cũ.

## Đánh đổi chấp nhận

- **`@SQLRestriction` không áp dụng cho native query.** Truy vấn tìm kiếm sản phẩm là native SQL (vì cần toán tử JSONB `@>`), nên ở đó phải tự viết `deleted_at IS NULL`. Đã ghi chú ngay trong `ProductRepositoryImpl`.
- Không thể xem hay khôi phục sản phẩm đã xóa qua JPA thông thường — `@SQLRestriction` chặn cả đường đọc. Cần native query nếu sau này làm màn hình "thùng rác".
- Bảng lớn dần theo thời gian.

## Khi nào nên đổi ý

- Khi cần tuân thủ "quyền được lãng quên" (GDPR) cho **dữ liệu cá nhân** — chỗ đó phải xóa thật hoặc ẩn danh hóa, xóa mềm không đủ. Quyết định này chỉ áp dụng cho dữ liệu sản phẩm.
- Khi bảng lớn tới mức partial index không còn đủ — lúc đó chuyển sản phẩm cũ sang bảng lưu trữ.

## Kiểm chứng

- `deleteProduct_softDeletes_andHidesFromPublicList`
- `deleteProduct_alreadyInOrder_doesNotBreakOrderHistory` — bắt đúng lỗi thiết kế của bản v1
