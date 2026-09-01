# Load test

Hai kịch bản, đo hai thứ khác nhau:

| Kịch bản | Đo cái gì |
|---|---|
| `browse.js` | **Hiệu năng** — p95 của endpoint tìm kiếm dưới 200 VU |
| `flashsale.js` | **Tính đúng đắn** — 500 người tranh N suất có bán quá không |

## Chuẩn bị

```bash
docker compose up -d postgres redis
SPRING_PROFILES_ACTIVE=demo APP_SEED_ENABLED=true mvn spring-boot:run
```

## 1. Duyệt sản phẩm

```bash
k6 run loadtest/browse.js
```

Đo **ba lượt**, mỗi lượt đổi đúng một thứ, ghi lại vào bảng ở [docs/06-nfr-va-load-test.md](../docs/06-nfr-va-load-test.md):

1. Nền — bỏ GIN index (`DROP INDEX idx_products_spec_gin;`)
2. Thêm lại GIN index
3. Thêm Redis cache-aside

Lượt đầu chạy chậm hơn do JVM chưa warm-up — chạy mỗi kịch bản 3 lần, lấy trung vị.

## 2. Flash sale

```bash
node loadtest/prepare-tokens.js                       # sinh tokens.json (500 token)
curl -s localhost:8080/api/v1/flash-sales/active      # lấy itemId của phiên đang chạy
k6 run -e ITEM_ID=<itemId> loadtest/flashsale.js
```

**Phần thuyết phục không nằm ở output của k6 mà ở ba câu SQL sau đó:**

```sql
-- sold_quantity phải ĐÚNG BẰNG counter `sold` của k6, không hơn một đơn vị
SELECT sold_quantity, sale_quantity FROM flash_sale_items WHERE id = <itemId>;

-- số đơn tạo ra phải khớp
SELECT COUNT(*) FROM orders WHERE created_at > now() - interval '5 minutes';

-- tồn kho không bao giờ âm
SELECT stock_quantity, reserved_quantity FROM product_variants WHERE id = <variantId>;
```

Chụp output k6 và kết quả ba câu SQL này cạnh nhau — đó là bằng chứng "không oversell dưới 500 request đồng thời" ở dạng khó bác bỏ nhất.

## Xuất báo cáo

```bash
k6 run --out web-dashboard=export=docs/loadtest/browse.html loadtest/browse.js
```

## Ghi số liệu trung thực

Luôn kèm cấu hình máy đo. "500 req/s trên laptop Ryzen 7, 16GB, Docker Desktop" là số liệu tử tế; "500 req/s" trống trơn khiến người đọc nghi ngờ mọi con số khác.

Và **giữ lại kết quả xấu** của lượt đo nền — nó là bối cảnh làm cho lượt cuối có ý nghĩa.
