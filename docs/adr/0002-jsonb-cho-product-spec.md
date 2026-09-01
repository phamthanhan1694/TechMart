# ADR-0002: JSONB cho thông số sản phẩm, không dùng EAV

- **Trạng thái:** Chấp nhận
- **Ngày:** 2026-09-01
- **Liên quan:** `01-domain-v2.md` §5.2

## Bối cảnh

Điện thoại có RAM/chipset/camera; laptop có CPU/GPU/trọng lượng. Hai bộ thuộc tính khác nhau, và sẽ còn khác nữa nếu thêm ngành hàng. Cột cố định trên bảng `products` không dùng được: mỗi ngành hàng mới là một lần `ALTER TABLE` và một nửa số cột luôn `NULL`.

Yêu cầu thật: khách lọc được theo thông số (`spec.ram=16GB`) trên trang danh sách, tức là **truy vấn phải nhanh**, không chỉ lưu được.

## Các phương án đã cân nhắc

| Phương án | Ưu | Nhược |
|---|---|---|
| Cột cố định | Ràng buộc kiểu chặt, index đơn giản | Không mở rộng được, bảng đầy cột NULL |
| **EAV** (`product_attributes(product_id, key, value)`) | Linh hoạt tối đa | Mỗi bộ lọc là một self-join; lọc 3 thuộc tính = 3 join. Không ràng buộc kiểu. Khó đọc |
| **JSONB + GIN index** | Một cột, lọc bằng `@>` dùng được index, đọc ra là thấy ngay | Không ràng buộc kiểu ở DB — `"ram": "8GB"` và `"ram": 8` cùng lọt |
| Bảng riêng cho từng ngành hàng | Ràng buộc chặt nhất | Mỗi ngành hàng mới là một bảng + một nhánh code |

## Quyết định

JSONB cho cả `products.spec` (thông số chung của dòng máy, dùng để lọc và so sánh) và `product_variants.attributes` (thứ tạo ra biến thể: màu, dung lượng). GIN index trên `products.spec`.

Bộ lọc `spec.<key>` ở API được **whitelist key** trước khi dựng JSON điều kiện — key là chuỗi tự do từ client, không được để đi thẳng xuống SQL.

## Đánh đổi chấp nhận

- **Không có ràng buộc kiểu ở DB.** Bù bằng validate ở tầng ứng dụng và một test kiểm tính nhất quán key trong cùng danh mục. Đây là nhược điểm phải thừa nhận thẳng khi được hỏi.
- Sửa một key trên toàn bộ sản phẩm là `UPDATE` với hàm JSONB, khó hơn `ALTER TABLE`.

## Khi nào nên đổi ý

- Khi admin cần **tự khai báo** thuộc tính qua giao diện, kèm kiểu và giá trị hợp lệ — lúc đó cần bảng `attribute_definitions` để mô tả schema, JSONB vẫn giữ nhưng có thứ để validate.
- Khi một thuộc tính trở thành trục nghiệp vụ chính (ví dụ lọc theo giá) — thứ đó nên là cột thật, không nằm trong JSON.

## Kiểm chứng

`searchProducts_bySpecJsonb_returnsMatching`. Và phép đo ở `06-nfr-va-load-test.md`: trên 50.000 sản phẩm, GIN index đưa p95 từ 2.220ms xuống 234ms — chứng minh JSONB vẫn truy vấn nhanh được, đúng điểm mà EAV thua.
