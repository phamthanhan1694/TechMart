# Kế hoạch dữ liệu mẫu

> Đây là tài liệu bị đánh giá thấp nhất và có tỷ lệ ấn tượng/công sức cao nhất trong cả bộ.
> Một shop 8 sản phẩm tên "Test Product 1" là dấu hiệu số một của đồ án bỏ dở. Cùng đúng lượng code đó, 350 sản phẩm thật làm project trông như đã chạy nhiều tháng.

---

## 1. Ba lý do dữ liệu mẫu quan trọng hơn bạn nghĩ

1. **Trải nghiệm người xem.** Interviewer mở Swagger gọi `GET /products` — thấy iPhone 15 Pro Max với spec đầy đủ khác hẳn thấy `{"name": "string", "price": 0}`.
2. **Số liệu load test chỉ có nghĩa khi có dữ liệu.** p95 trên 8 dòng dữ liệu là con số vô nghĩa; index không thể hiện tác dụng gì. Cần tối thiểu vài trăm sản phẩm để `EXPLAIN` cho ra Seq Scan vs Index Scan khác biệt.
3. **Dashboard doanh thu cần lịch sử.** Đây là chỗ 90% người làm bỏ sót: họ seed sản phẩm nhưng không seed **đơn hàng lịch sử**, nên biểu đồ doanh thu rỗng và không demo được. Xem mục 4.

---

## 2. Khối lượng mục tiêu

| Bảng | Số lượng | Ghi chú |
|---|---|---|
| `brands` | 12 | Apple, Samsung, Xiaomi, OPPO, Dell, HP, Lenovo, ASUS, Acer, MSI, Google, Sony |
| `categories` | 6 | Điện thoại → (Flagship, Tầm trung, Phổ thông); Laptop → (Gaming, Văn phòng, Đồ họa) |
| `products` | **350** | ~180 điện thoại, ~170 laptop |
| `product_variants` | **~900** | Trung bình 2,5 variant/sản phẩm (màu × dung lượng) |
| `product_images` | ~1400 | 4 ảnh/sản phẩm |
| `users` | **520** | 500 customer (phục vụ load test) + 15 staff + 5 admin |
| `coupons` | 8 | Đủ loại: percent, fixed, hết hạn, hết lượt, chưa bắt đầu |
| `orders` | **~1200** | **Rải trong 90 ngày gần nhất** — xem mục 4 |
| `order_items` | ~2800 | |
| `reviews` | ~600 | Chỉ gắn với `order_item` của đơn `DELIVERED` |
| `flash_sales` | 3 | 1 đã kết thúc, 1 đang chạy, 1 sắp diễn ra |
| `product_serials` | ~2000 | Nếu làm tính năng serial |

500 customer không phải để trưng bày — script k6 ở `06-nfr-va-load-test.md` cần 500 token khác nhau để mô phỏng flash sale cho đúng (`per_user_limit` sẽ chặn nếu dùng chung vài user).

---

## 3. Nguồn dữ liệu

**Không crawl website thương mại điện tử thật** — điều khoản sử dụng, và không đáng rủi ro cho một project portfolio.

Cách làm, theo thứ tự ưu tiên:

1. **Soạn tay ~60 sản phẩm "hạt giống"** có thật và chính xác (iPhone 15/14/13 các bản, Galaxy S24/S23, MacBook Air M2/M3, ThinkPad X1, Dell XPS 13/15, ROG Strix...). Spec tra từ trang chủ hãng. Đây là phần tốn công nhất, khoảng 2–3 tiếng.
2. **Sinh 290 sản phẩm còn lại bằng tổ hợp** từ hạt giống: đổi dung lượng, đổi màu, đổi năm ra mắt, đổi cấu hình RAM/CPU. Giá suy ra theo công thức từ cấu hình. Kết quả hợp lý và đa dạng mà không phải gõ tay.
3. **Ảnh**: dùng URL ảnh chính thức từ trang hãng, hoặc placeholder có nội dung (`https://placehold.co/600x600?text=iPhone+15`). Đừng để `null` — response API trông dở hẳn.

**Spec phải nhất quán về key** trong cùng một danh mục, nếu không bộ lọc `spec.ram=16GB` sẽ trả thiếu:

```json
// Điện thoại — dùng đúng bộ key này cho mọi sản phẩm điện thoại
{ "screen": "6.7 inch OLED", "refreshRate": "120Hz", "chipset": "A17 Pro",
  "ram": "8GB", "storage": "256GB", "battery": "4441mAh",
  "camera": "48MP + 12MP + 12MP", "os": "iOS 17", "releaseYear": 2023 }

// Laptop — bộ key khác, và đó chính là lý do dùng JSONB
{ "screen": "14 inch Retina", "cpu": "Apple M3", "gpu": "10-core GPU",
  "ram": "16GB", "storage": "512GB SSD", "battery": "70Wh",
  "weight": "1.24kg", "os": "macOS Sonoma", "releaseYear": 2024 }
```

Viết một test kiểm tra tính nhất quán này: mọi sản phẩm cùng `category` phải có cùng tập key trong `spec`. Rẻ, và bắt được lỗi seed sớm.

---

## 4. Đơn hàng lịch sử — phần hay bị bỏ sót

Dashboard doanh thu ở `03-api-contract.md` mục 11 cần dữ liệu 90 ngày để vẽ được biểu đồ. Không có nó, endpoint chạy đúng nhưng **demo ra một đường thẳng bằng 0** và bạn mất luôn cơ hội khoe query aggregate.

Quy tắc sinh đơn hàng:

- **Rải theo 90 ngày**, mật độ tăng dần về hiện tại (mô phỏng shop đang lớn)
- **Có tính mùa vụ**: cuối tuần cao hơn ngày thường ~40%; ba đợt cao điểm quanh 9/9, 10/10, 11/11 để biểu đồ có đỉnh nhìn ra được
- **Phân bố trạng thái thực tế**: 70% `DELIVERED`, 10% `SHIPPED`, 8% `PAID`, 7% `CANCELLED`, 5% `PENDING_PAYMENT`
- **Giá trị đơn lệch phải**: nhiều đơn nhỏ, ít đơn lớn — không phân bố đều
- **`created_at` phải khớp `order_status_history`**: đơn `DELIVERED` ngày 20/8 thì phải có bản ghi lịch sử đúng thứ tự thời gian trước đó. Chi tiết này khiến `GET /orders/{code}/history` demo được tử tế
- **Tồn kho phải nhất quán**: sau khi seed xong, `stock_quantity` của mỗi variant phải bằng `stock ban đầu − đã bán + đã hủy`. Nếu không, số liệu tự mâu thuẫn và người tinh ý sẽ thấy

> Bước cuối cùng: chạy một script đối soát sau khi seed, kiểm ba bất biến — tồn kho khớp, `reserved_quantity = 0`, tổng `order_items.line_total` bằng `orders.subtotal` từng đơn. Đây vừa là kiểm tra chất lượng seed, vừa là bản nháp cho job đối soát nói ở `01-domain-v2.md` mục 5.1.

---

## 5. Cách chạy seed

Dùng `CommandLineRunner` có điều kiện, **không** dùng Flyway migration:

```java
@Component
@Profile({"dev", "demo"})
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataSeeder implements CommandLineRunner {

    @Override
    public void run(String... args) {
        if (productRepository.count() > 0) {
            log.info("Đã có dữ liệu, bỏ qua seed");
            return;   // idempotent — chạy lại không nhân đôi
        }
        seedBrandsAndCategories();
        seedProductsAndVariants();
        seedUsers();
        seedCoupons();
        seedHistoricalOrders();   // 90 ngày
        seedFlashSales();
        verifyInvariants();       // ném lỗi nếu seed sai
    }
}
```

**Vì sao không để trong Flyway?** Migration là schema, chạy ở mọi môi trường gồm cả production. Dữ liệu demo không nên có ở production. Tách ra cũng để bật/tắt được bằng một biến môi trường — và đây là câu trả lời gọn cho "sao không dùng `R__seed.sql`".

Thêm vào `docker-compose.yml`: `APP_SEED_ENABLED=true`, `SPRING_PROFILES_ACTIVE=demo`. Người lạ clone repo, chạy một lệnh, mở Swagger là có ngay 350 sản phẩm.

Thời gian seed phải dưới 30 giây — dùng batch insert (`spring.jpa.properties.hibernate.jdbc.batch_size=50`), không insert từng dòng. Nếu seed mất 5 phút thì chính bạn sẽ bực mỗi lần khởi động lại.

---

## 6. Tài khoản demo (ghi vào README)

| Vai trò | Email | Mật khẩu | Trạng thái dữ liệu |
|---|---|---|---|
| ADMIN | `admin@techmart.dev` | `Admin@123` | Toàn quyền, xem được dashboard |
| STAFF | `staff@techmart.dev` | `Staff@123` | Xử lý đơn, không xem được doanh thu |
| CUSTOMER | `customer@techmart.dev` | `Customer@123` | **Có sẵn 12 đơn lịch sử, 3 đơn đang giao, 1 giỏ hàng 3 món** |
| CUSTOMER (trống) | `new@techmart.dev` | `Customer@123` | Tài khoản mới tinh, để demo luồng từ đầu |

Tài khoản `customer@techmart.dev` **phải có sẵn dữ liệu**. Interviewer đăng nhập, gọi `GET /orders` mà trả mảng rỗng thì mọi endpoint phía sau đều mất ý nghĩa. Đây là chi tiết nhỏ quyết định 30 giây đầu tiên của người xem.

Tài khoản trống là để bạn demo trọn luồng thêm giỏ → checkout → thanh toán mà không lẫn với dữ liệu cũ.

---

## 7. Checklist

- [ ] 60 sản phẩm hạt giống soạn tay, spec chính xác
- [ ] Bộ sinh tổ hợp ra đủ 350 sản phẩm / ~900 variant
- [ ] Spec nhất quán key theo danh mục (có test kiểm)
- [ ] Mọi sản phẩm có ảnh (URL thật hoặc placeholder có chữ)
- [ ] 500 customer + script sinh `tokens.json` cho k6
- [ ] ~1200 đơn rải 90 ngày, có mùa vụ, có `order_status_history` khớp thời gian
- [ ] Tồn kho nhất quán sau seed (script đối soát chạy sạch)
- [ ] 3 flash sale ở ba trạng thái khác nhau
- [ ] 8 coupon đủ các tình huống (hợp lệ, hết hạn, hết lượt, chưa bắt đầu)
- [ ] Seed idempotent, chạy dưới 30 giây, bật/tắt bằng biến môi trường
- [ ] Bảng tài khoản demo trong README
