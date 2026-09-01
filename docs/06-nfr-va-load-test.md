# Yêu cầu phi chức năng & Load test

> Mục tiêu của tài liệu này không phải là "chứng minh hệ thống nhanh". Là **có số liệu thật để nói chuyện**.
> Một câu "p95 giảm từ 380ms xuống 42ms sau khi thêm GIN index và cache" đáng giá hơn mười dòng mô tả tính năng.

---

## 1. Mục tiêu NFR

Đo trên máy local, 1 instance app, PostgreSQL + Redis trong Docker. **Ghi rõ cấu hình máy trong README** — số liệu không kèm môi trường là số liệu vô nghĩa, và interviewer giỏi sẽ hỏi ngay.

| Chỉ số | Mục tiêu | Đo ở đâu |
|---|---|---|
| p95 `GET /products` (có cache) | < 80 ms | k6 kịch bản duyệt sản phẩm |
| p95 `GET /products` (không cache) | < 300 ms | Cùng kịch bản, tắt cache |
| p95 `POST /orders` | < 400 ms | k6 kịch bản đặt hàng |
| Throughput `GET /products` | > 500 req/s | |
| Tỉ lệ lỗi 5xx | **0%** dưới mọi kịch bản | Bất kỳ 5xx nào cũng là bug, không phải giới hạn tải |
| Flash sale: số đơn thành công | **Đúng bằng** `sale_quantity` | Đây là chỉ số *đúng đắn*, không phải hiệu năng — và là chỉ số quan trọng nhất |
| Flash sale: app không sập | Không OOM, không exhausted connection pool | |

Dòng áp chót là điểm mấu chốt: dưới tải cực đại, thứ bạn bảo vệ là **tính đúng đắn**, không phải tốc độ. Nói rõ điều đó là dấu hiệu tư duy kỹ sư.

---

## 2. Quy trình đo (làm đúng thứ tự này)

1. **Đo trước khi tối ưu bất cứ thứ gì.** Không có số nền thì mọi cải thiện đều là cảm tính.
2. **Thay đúng một thứ**, đo lại. Thêm index → đo. Bật cache → đo. Đổi connection pool → đo. Đổi ba thứ cùng lúc rồi thấy nhanh hơn là không biết cái nào có tác dụng.
3. Ghi mọi lượt đo vào bảng ở mục 5, kể cả lượt **không** cải thiện. Một dòng "thêm index trên `brand_id` — không đổi, vì bộ lọc này ít dùng" cho thấy bạn đo thật chứ không chỉ chép kết quả đẹp.
4. Chạy mỗi kịch bản 3 lần, lấy trung vị. Lượt đầu luôn chậm do JIT warm-up của JVM.

---

## 3. Kịch bản k6

### 3.1. Duyệt sản phẩm — `loadtest/browse.js`

```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const listLatency = new Trend('list_latency');
const BASE = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  stages: [
    { duration: '30s', target: 50 },   // tăng dần
    { duration: '1m',  target: 200 },  // giữ tải
    { duration: '30s', target: 0 },    // hạ
  ],
  thresholds: {
    'http_req_duration{name:list}': ['p(95)<80'],
    'http_req_failed': ['rate<0.01'],
  },
};

const KEYWORDS = ['iphone', 'macbook', 'samsung', 'thinkpad', 'xiaomi'];
const RAMS = ['8GB', '16GB', '32GB'];

export default function () {
  const kw = KEYWORDS[Math.floor(Math.random() * KEYWORDS.length)];
  const ram = RAMS[Math.floor(Math.random() * RAMS.length)];

  const res = http.get(
    `${BASE}/api/v1/products?keyword=${kw}&spec.ram=${ram}&page=0&size=20`,
    { tags: { name: 'list' } }
  );
  listLatency.add(res.timings.duration);
  check(res, { 'status 200': (r) => r.status === 200 });

  const detail = http.get(`${BASE}/api/v1/products/iphone-15-pro-max`, { tags: { name: 'detail' } });
  check(detail, { 'detail 200': (r) => r.status === 200 });

  sleep(Math.random() * 2);
}
```

Chú ý keyword và spec **ngẫu nhiên**: bắn mãi một truy vấn giống hệt thì cache hit 100% và số liệu đẹp giả tạo. Cache hit rate thực tế nên rơi vào 60–80%.

### 3.2. Flash sale — `loadtest/flashsale.js`

```javascript
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import exec from 'k6/execution';

const sold      = new Counter('sold');
const soldOut   = new Counter('sold_out');
const throttled = new Counter('throttled');
const serverErr = new Counter('server_error');

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const TOKENS = JSON.parse(open('./tokens.json'));   // 500 token seed sẵn

export const options = {
  scenarios: {
    thundering_herd: {
      executor: 'shared-iterations',
      vus: 500,
      iterations: 500,
      maxDuration: '1m',
    },
  },
  thresholds: { 'server_error': ['count==0'] },
};

export default function () {
  const token = TOKENS[exec.vu.idInTest % TOKENS.length];
  const res = http.post(
    `${BASE}/api/v1/flash-sales/1/items/1/purchase`,
    JSON.stringify({ quantity: 1 }),
    { headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
        'Idempotency-Key': `${exec.vu.idInTest}-${exec.scenario.iterationInTest}`,
    }}
  );

  if (res.status === 201) sold.add(1);
  else if (res.status === 409) soldOut.add(1);
  else if (res.status === 429) throttled.add(1);
  else if (res.status >= 500) serverErr.add(1);

  check(res, { 'không 5xx': (r) => r.status < 500 });
}
```

**Sau khi chạy, kiểm chứng bằng SQL** — đây mới là phần thuyết phục:

```sql
SELECT sold_quantity, sale_quantity FROM flash_sale_items WHERE id = 1;
-- sold_quantity phải ĐÚNG BẰNG sale_quantity, không hơn một đơn vị

SELECT COUNT(*) FROM orders WHERE created_at > now() - interval '5 minutes';
-- phải bằng counter `sold` của k6

SELECT stock_quantity, reserved_quantity FROM product_variants WHERE id = 1;
-- không được âm
```

Chụp lại đầu ra k6 + kết quả ba câu SQL này cạnh nhau. Đó là bằng chứng "không oversell dưới 500 request đồng thời" ở dạng khó bác bỏ nhất.

---

## 4. Nút thắt dự kiến và thứ tự xử lý

Đoán trước để khi số liệu ra khớp thì bạn có câu chuyện, và khi không khớp thì bạn học được gì đó.

| Thứ tự | Nút thắt | Dấu hiệu | Cách xử lý |
|---|---|---|---|
| 1 | Thiếu index trên bộ lọc | p95 tăng tuyến tính theo số sản phẩm; `EXPLAIN` thấy Seq Scan | GIN cho `spec`, trigram cho `name`, B-tree cho `price` |
| 2 | N+1 khi list product kèm variant | Số query tăng theo `size` | `@EntityGraph` hoặc query gộp |
| 3 | Connection pool cạn | `HikariPool - Connection is not available, request timed out` | Tăng `maximum-pool-size`, nhưng **trước hết** rút ngắn transaction |
| 4 | Transaction giữ khóa quá lâu | Đặt hàng chậm dần khi tải tăng | Đẩy mọi việc không cần thiết (gửi mail, ghi log) ra ngoài transaction — đây chính là lý do có outbox |
| 5 | Serialize JSON | CPU cao, latency đều | Cache ở dạng đã serialize sẵn |

**Không tăng connection pool trước khi rút ngắn transaction.** Pool lớn hơn chỉ dời điểm nghẽn sang DB và làm mọi thứ chậm đều. Trả lời được ý này khi được hỏi "hệ thống chậm thì bạn làm gì đầu tiên" là điểm cộng lớn — hầu hết ứng viên trả lời "tăng pool" hoặc "thêm cache".

---

## 5. Kết quả đo được

**Môi trường:** Intel Core i5-11320H (4 nhân / 8 luồng, 3.2GHz) · 16GB RAM · Windows 11 · JDK 26 · Docker Desktop (WSL2) · PostgreSQL 16 + Redis 7 trong container · 1 instance app · k6 v2.2.0 chạy trên cùng máy.

### 5.1. Một phát hiện phải nói trước

Đo trên **dữ liệu demo 345 sản phẩm thì GIN index không có tác dụng gì** — `EXPLAIN` cho thấy PostgreSQL chọn Seq Scan và bỏ qua index hoàn toàn, vì quét cả bảng 345 dòng còn rẻ hơn đi qua index:

```
Seq Scan on products p  (cost=0.00..28.04 rows=17) (actual time=0.012..0.197 rows=22)
  Rows Removed by Filter: 323
```

Đo "trước/sau khi thêm index" ở quy mô đó sẽ ra chênh lệch bằng 0, và báo cáo nó như một cải thiện là không trung thực. Catalog thật của một cửa hàng điện tử có hàng chục nghìn SKU, nên phép đo dưới đây chạy trên **50.000 sản phẩm** (sinh bằng [`loadtest/bulk-data.sql`](../loadtest/bulk-data.sql), dọn bằng `cleanup-bulk.sql`). Ở quy mô này planner mới dùng index:

```
Bitmap Heap Scan on products p  (cost=145.69..1987.97 rows=1018) (actual time=3.308..7.810 rows=2106)
  ->  BitmapAnd
        ->  Bitmap Index Scan on idx_products_spec_gin   (actual time=1.887..1.888 rows=8468)
        ->  Bitmap Index Scan on idx_products_name_trgm
```

### 5.2. Kịch bản duyệt sản phẩm

50.000 sản phẩm / 50.850 variant · 100 VU · 60 giây · truy vấn `keyword` + `spec.ram` + `sort` ngẫu nhiên mỗi lần.
Mỗi cấu hình chạy nhiều lượt, lấy **trung vị** (lượt đầu luôn chậm hơn do JVM chưa warm-up).

| Cấu hình | p95 `GET /products` | Throughput | Kế hoạch truy vấn |
|---|---|---|---|
| **Không có GIN index** (3 lượt) | **2.220 ms** | **58,0 req/s** | Seq Scan, loại bỏ 48.239 dòng mỗi truy vấn |
| **Có GIN index** (4 lượt) | **234 ms** | **163,1 req/s** | BitmapAnd trên hai GIN index |
| | **↓ 9,5 lần** | **↑ 2,8 lần** | |

Số liệu từng lượt:

| Cấu hình | p95 các lượt (ms) | Throughput các lượt (req/s) |
|---|---|---|
| Không index | 1.710 · 2.850 · 2.220 | 64,7 · 52,0 · 58,0 |
| Có index | 98,9 · 210,9 · 400,3 · 256,6 | 170,1 · 165,2 · 153,1 · 160,9 |

**Vì sao p95 khi có index dao động rộng (99–400ms) còn throughput thì ổn định?** Ở 100 VU hệ thống đã gần bão hòa (~163 req/s là trần), nên p95 lúc này đo **thời gian xếp hàng** chứ không đo thời gian truy vấn. Throughput mới là con số phản ánh đúng năng lực. Muốn đo latency thuần thì phải hạ tải xuống dưới ngưỡng bão hòa.

Tỉ lệ lỗi ở cả hai cấu hình: **0%** (`http_req_failed: 0.00%`). Không index thì chậm, nhưng không sai.

### 5.3. Chưa đo

Redis cache-aside cho danh sách sản phẩm **chưa làm** — Redis hiện phục vụ reservation fast-path, rate limit, bộ đếm flash sale và idempotency. Nếu thêm cache thì đây là lượt đo thứ ba của bảng trên.

## 6. Cách trình bày trong README (và cách không phóng đại)

Viết như thế này:

> **Kết quả đo (local, Ryzen 7 5800H / 16GB / Docker Desktop, 1 instance, dữ liệu 350 sản phẩm):**
> `GET /products` với bộ lọc spec: p95 **380ms → 42ms** sau khi thêm GIN index và Redis cache-aside (200 VU).
> Flash sale 50 suất / 500 request đồng thời: **đúng 50 đơn thành công**, 0 lỗi 5xx, `sold_quantity` trong DB khớp tuyệt đối. Trước khi dùng Redis Lua, cùng kịch bản cho ra 63 đơn — oversell 13 đơn.

Ba nguyên tắc trung thực, và cả ba đều làm bạn *đáng tin hơn* chứ không yếu đi:

1. **Luôn kèm môi trường đo.** "500 req/s trên laptop" là số liệu tử tế; "500 req/s" trống trơn thì interviewer sẽ nghi ngờ mọi con số khác của bạn.
2. **Không suy diễn ra production.** Không viết "hệ thống chịu được 10.000 người dùng". Bạn không đo cái đó.
3. **Giữ lại kết quả xấu.** Con số "oversell 13 đơn trước khi sửa" là phần giá trị nhất của cả bảng — nó chứng minh vấn đề có thật và bạn đã đo được nó.

---

## 7. Việc cần làm trong tuần 5

- [ ] Cài k6, viết hai script trên vào `loadtest/`
- [ ] Sinh `tokens.json` (500 user seed sẵn, đăng nhập lấy token bằng script)
- [ ] Chạy đủ 5 lượt kịch bản duyệt + 3 lượt flash sale, điền bảng
- [ ] Xuất HTML report (`k6 run --out web-dashboard`), commit vào `docs/loadtest/`
- [ ] Chụp Grafana lúc đỉnh tải, lưu `docs/img/grafana-flashsale.png`
- [ ] Chạy `EXPLAIN ANALYZE` cho 3 truy vấn nặng nhất, lưu output trước/sau index vào `docs/explain/`
- [ ] Viết mục "Kết quả đo" trong README theo mẫu mục 6
