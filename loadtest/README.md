# Kiểm thử tải

Bộ kịch bản k6 cho API TechMart, gồm đo độ trễ truy vấn sản phẩm và kiểm tra hành vi mua flash sale dưới tải đồng thời. Các lệnh dưới đây chạy bằng PowerShell tại thư mục gốc dự án.

## Điều kiện chạy

- Docker Engine và Docker Compose; k6; Node.js 24.
- Backend kết nối được PostgreSQL và Redis, đã có dữ liệu demo.
- Database và tài khoản dành riêng cho kiểm thử. Kịch bản flash sale tạo đơn hàng và trừ tồn kho thực trong môi trường được chọn.

```powershell
docker compose up -d --build
Invoke-RestMethod http://localhost:8080/actuator/health
```

Chỉ bắt đầu khi health check trả `status: UP`. Địa chỉ API mặc định là `http://localhost:8080`; tham số `BASE_URL` cho phép chọn môi trường khác.

## Truy vấn sản phẩm

```powershell
k6 run loadtest/browse.js
```

Kịch bản thực hiện tìm kiếm theo từ khóa, RAM và thứ tự sắp xếp được chọn ngẫu nhiên. Nếu danh sách có dữ liệu, kịch bản gọi thêm API chi tiết một sản phẩm. Mỗi vòng lặp nghỉ ngẫu nhiên từ 0 đến 2 giây.

Tải được tăng từ 0 lên 50 virtual users (VU) trong 30 giây, từ 50 lên 200 VU trong 60 giây, sau đó giảm về 0 trong 30 giây. Tổng thời gian các giai đoạn là 2 phút; thời gian thực tế có thể dài hơn do hoàn tất request đang chạy.

Ngưỡng đạt được khai báo trong script:

- `http_req_failed`: tỷ lệ HTTP request thất bại dưới 1%.
- `list_latency`: phân vị 95 của độ trễ API danh sách dưới 300 ms.

`detail_latency` chỉ được ghi nhận, chưa có ngưỡng riêng. Các check `list 200` và `detail 200` kiểm tra HTTP 200 nhưng chưa có threshold cho tỷ lệ check thành công.

Chạy với địa chỉ API khác:

```powershell
k6 run -e BASE_URL=http://localhost:8080 loadtest/browse.js
```

## Dữ liệu catalog mở rộng

`bulk-data.sql` tạo 50.000 sản phẩm có slug bắt đầu bằng `loadtest-`, mỗi sản phẩm một variant, sau đó chạy `ANALYZE`. Database phải có sẵn danh mục và thương hiệu.

```powershell
Get-Content loadtest/bulk-data.sql -Raw -Encoding UTF8 |
    docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U techmart -d techmart
```

Tên database và user trong lệnh sử dụng cấu hình mặc định. Thay chúng nếu môi trường dùng thông tin kết nối khác. Script không idempotent: chạy lại trên tập dữ liệu đã tạo sẽ gặp xung đột slug hoặc SKU.

Thay đổi trực tiếp bằng SQL không kích hoạt cơ chế xóa cache của ứng dụng. Trước khi đo, bảo đảm cache đã hết hạn hoặc sử dụng cấu hình cache thống nhất giữa các lượt chạy.

Dọn dữ liệu catalog đã sinh sau khi đo:

```powershell
Get-Content loadtest/cleanup-bulk.sql -Raw -Encoding UTF8 |
    docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U techmart -d techmart
```

Script chỉ nhắm tới sản phẩm có slug `loadtest-` và các variant, ảnh tương ứng; không dọn đơn hàng flash sale. Không dùng các sản phẩm này để tạo đơn trước khi dọn, vì ràng buộc khóa ngoại có thể chặn việc xóa.

## Mua flash sale

### Chuẩn bị token và phiên bán

```powershell
node loadtest/prepare-tokens.js
node -e "const t=require('./loadtest/tokens.json'); console.log('Tokens:',t.length,'Unique:',new Set(t).size)"
Invoke-RestMethod http://localhost:8080/api/v1/flash-sales/active |
    ConvertTo-Json -Depth 10
```

Script tạo token đăng nhập lần lượt cho `buyer1@techmart.dev` đến `buyer500@techmart.dev`, dùng mật khẩu demo `Customer@123` và tối đa 25 request đăng nhập song song. Kết quả ghi vào `loadtest/tokens.json`.

`BASE_URL` và `COUNT` là biến môi trường của Node.js:

```powershell
$env:BASE_URL = 'http://localhost:8080'
$env:COUNT = '500'
node loadtest/prepare-tokens.js
```

Phải có đủ 500 token khác nhau cho cấu hình tải mặc định. Script chỉ cảnh báo khi thiếu token, không tự retry và không trả mã lỗi cho trường hợp thiếu. Token thiếu hoặc hết hạn làm sai mục tiêu phép đo; tạo lại ngay trước khi chạy. Không đưa file token vào Git.

Chọn `items[].id` trong phản hồi API làm `ITEM_ID`; không dùng ID của phiên bán hoặc variant. Phiên phải còn hiệu lực, còn suất, có đủ tồn kho vật lý và đã nạp bộ đếm Redis. Ghi nhận `sold_quantity`, tồn kho và thời điểm bắt đầu trước khi phát tải.

### Phát tải

```powershell
$itemId = 1 # Thay bằng items[].id cần kiểm thử
k6 run -e BASE_URL=http://localhost:8080 -e ITEM_ID=$itemId loadtest/flashsale.js
```

Cấu hình sử dụng `shared-iterations` với 500 VU, tổng cộng 500 iterations và thời hạn tối đa 2 phút. Mỗi iteration gửi một request mua số lượng 1. Đây là lượt phát tải cạnh tranh, không bảo đảm 500 request đến server cùng thời điểm hoặc mỗi VU thực hiện đúng một iteration.

Các bộ đếm được phân loại theo HTTP status:

- `sold`: HTTP 201.
- `sold_out`: HTTP 409; bao gồm cả lỗi vượt giới hạn mỗi người và các xung đột khác, không chỉ hết suất.
- `throttled`: HTTP 429.
- `server_error`: HTTP từ 500 trở lên.

Threshold hiện tại chỉ yêu cầu `server_error` bằng 0. Các phản hồi 400, 401, 403 và lỗi mạng chưa được phân loại đầy đủ; `http_req_failed` chưa có threshold. Kết quả k6 đạt threshold chưa đủ để kết luận không bán quá tồn kho.

### Rate limiting và chạy lại

Cấu hình backend mặc định giới hạn đăng nhập 60 request/phút/IP; các POST flash sale bị giới hạn 100 request/phút/IP và 10 request/phút/user. Tải 500 request từ một máy có thể bị trả 429 trước khi chạm logic tồn kho. Khi đo riêng khả năng xử lý tồn kho, cần môi trường kiểm thử với cấu hình giới hạn phù hợp và ghi rõ cấu hình đó trong kết quả.

Idempotency key hiện được tạo từ ID VU và số iteration, không có mã riêng cho mỗi lượt chạy. Chạy lại trên cùng database có thể trùng key và nhận lại đơn cũ. Dùng trạng thái dữ liệu kiểm thử tách biệt giữa các lượt; đổi `ITEM_ID` không loại bỏ nguy cơ trùng key.

### Đối soát dữ liệu

Trong phiên `psql`, đặt `item_id` đúng với giá trị đã dùng để phát tải rồi chạy truy vấn này trước và sau lượt đo:

```sql
\set item_id 1

SELECT f.id, f.variant_id, f.sale_quantity, f.sold_quantity,
       f.sale_quantity - f.sold_quantity AS remaining_db,
       v.stock_quantity, v.reserved_quantity
FROM flash_sale_items f
JOIN product_variants v ON v.id = f.variant_id
WHERE f.id = :item_id;
```

Đọc bộ đếm Redis sau khi các request đã hoàn tất:

```powershell
docker compose exec -T redis redis-cli GET "fs:${itemId}:stock"
```

Điều kiện đối soát trong môi trường không có giao dịch khác hoặc hủy đơn đồng thời:

- `0 <= sold_quantity <= sale_quantity`.
- `stock_quantity >= 0` và `0 <= reserved_quantity <= stock_quantity`.
- Tồn Redis bằng `sale_quantity - sold_quantity`; key thiếu cần được kiểm tra riêng.
- Mức tăng `sold_quantity`, mức giảm tồn vật lý và tổng số lượng trong đơn mới của lượt đo phải khớp nhau.
- Với lượt chạy không có replay idempotency, mỗi request mua 1, mức tăng `sold_quantity` phải bằng bộ đếm `sold`.

Đơn flash sale được đánh dấu `note = 'Flash sale'`. Khi đếm đơn, lọc thêm variant và khoảng thời gian chính xác của lượt đo; không dùng tổng số đơn toàn database. HTTP 201 do replay không chứng minh có đơn mới được tạo.

## Lưu kết quả và giám sát

```powershell
New-Item -ItemType Directory -Force out | Out-Null
k6 run --summary-export=out/browse-summary.json loadtest/browse.js
k6 run --summary-export=out/flashsale-summary.json -e ITEM_ID=$itemId loadtest/flashsale.js
```

Hai lệnh trên phát tải thực, không chỉ xuất lại kết quả đã có. Chỉ chạy lệnh tương ứng khi môi trường đã được chuẩn bị. File summary chứa số liệu tổng hợp; thời gian chạy và dữ liệu đối soát cần được lưu kèm.

Khởi động giám sát:

```powershell
docker compose --profile observability up -d
```

Prometheus chạy tại `http://localhost:9090`; Grafana tại `http://localhost:3001`, tài khoản mặc định `admin` / `admin`. Dashboard TechMart theo dõi backend, không tự nhận các custom metric của k6 như `sold` hoặc `list_latency`.

Mỗi kết quả cần ghi rõ phiên bản mã nguồn, cấu hình CPU/RAM, giới hạn tài nguyên container, quy mô dữ liệu, trạng thái cache, rate limit, thông số tải và số request hoàn tất. Với phép đo độ trễ, warm-up trước, chạy ít nhất ba lượt cùng điều kiện và giữ riêng kết quả từng lượt. Không dùng kết quả chỉ có 429, dữ liệu rỗng hoặc cache hit gần tuyệt đối để đại diện cho năng lực xử lý nghiệp vụ.
