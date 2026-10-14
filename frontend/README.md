# TechMart — Frontend

Giao diện cho [TechMart backend](../README.md): cửa hàng cho khách và khu quản trị cho nhân viên.

`Next.js 16 (App Router)` · `React 19` · `TypeScript strict` · `Tailwind CSS v4` · `lucide-react`

Cùng khuôn với [`frontend-graphrag/`](../frontend-graphrag): token thiết kế trong `@theme`, lớp
component dùng `@apply`, dark mode bằng class `.dark` trên `<html>`, chống FOUC bằng script chèn
trước lúc hydrate, alias `@/*` → `src/*`, và `app/` chỉ là vỏ mỏng gọi xuống `views/`.

---

## Chạy

Cả stack bằng Docker, từ thư mục gốc của repo:

```bash
docker compose up -d --build      # postgres + redis + backend + frontend
```

Hoặc chạy riêng frontend khi đang sửa mã:

```bash
npm install
npm run dev          # http://localhost:3000
```

Backend phải chạy ở `http://localhost:8080` (xem [README gốc](../README.md)). CORS phía backend đã
mở sẵn cho `localhost:3000` — nhưng ở chế độ mặc định request đi qua route proxy của Next nên không
chạm tới CORS.

Tài khoản có sẵn trong seed — trang đăng nhập có ba nút bấm là vào thẳng:

| Vai trò | Email | Mật khẩu |
|---|---|---|
| CUSTOMER | `customer@techmart.dev` | `Customer@123` |
| STAFF | `staff@techmart.dev` | `Staff@123` |
| ADMIN | `admin@techmart.dev` | `Admin@123` |

### Biến môi trường

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `BACKEND_URL` | `http://localhost:8080` | Đích của route proxy, đọc **lúc có request** nên đổi được mà không cần build lại |
| `NEXT_PUBLIC_API_BASE_URL` | rỗng | Rỗng: browser gọi `/api/...` rồi Next proxy sang backend (không dính CORS). Đặt `http://localhost:8080` để gọi thẳng backend. |

---

## Cấu trúc

```
src/
├── app/                    # route — mỗi file chỉ ráp guard + shell + view
│   ├── layout.tsx          # font, providers, script chống FOUC
│   ├── globals.css         # @theme tokens + @layer components
│   ├── api/[...path]/      # proxy sang backend, đọc BACKEND_URL lúc có request
│   ├── products/[slug]/    # params là Promise (Next 15+)
│   └── admin/layout.tsx    # chặn ADMIN|STAFF cho cả nhánh
├── components/             # dùng lại nhiều nơi: shell, badge, phân trang, modal…
├── contexts/               # Theme, Auth, Cart, Toast
├── lib/
│   ├── api/client.ts       # fetch + Bearer + xoay vòng refresh token
│   ├── api/<module>.ts     # một file cho mỗi module backend
│   ├── auth.ts             # lưu phiên trong localStorage
│   └── format.ts           # tiền, ngày, đếm ngược, nhãn thông số
├── types/index.ts          # đối chiếu 1-1 với các record DTO của backend
└── views/                  # màn hình thật, nhóm theo miền nghiệp vụ
```

---

## Bốn chỗ đáng đọc kỹ

### 1. Xoay vòng refresh token — gộp vào một lần gọi

Backend cấp access token 15 phút và refresh token 7 ngày **có xoay vòng**: mỗi lần
`POST /auth/refresh` là token cũ bị thu hồi ngay, và dùng lại token đã thu hồi sẽ khiến backend thu
hồi **toàn bộ chuỗi token** của người dùng (chống replay).

Một trang thường bắn nhiều request song song. Access token hết hạn giữa chừng thì tất cả cùng nhận
401 và cùng muốn refresh — gọi song song là có request cầm token vừa bị thu hồi, và người đang dùng
bình thường bị đá ra ngoài.

Nên [`client.ts`](src/lib/api/client.ts) gom mọi lần refresh vào **một promise duy nhất**
(single-flight): request 401 đầu tiên đi refresh, các request sau chờ chung kết quả đó rồi phát lại.
Mỗi request chỉ thử lại đúng một lần.

### 2. `Idempotency-Key` sinh một lần, không sinh lại mỗi lần bấm

Đặt hàng, thanh toán và mua suất flash sale đều bắt buộc header này. Điểm dễ sai: sinh UUID mới ở
**mỗi lần bấm nút**. Làm vậy thì khách bấm hai lần vì mạng chậm sẽ ra hai đơn — đúng thứ mà header
này sinh ra để chặn.

Ở [`CheckoutView`](src/views/checkout/CheckoutView.tsx) key được sinh một lần bằng `useRef` cho cả
lượt đặt hàng và giữ nguyên qua mọi lần bấm lại; ở flash sale, mỗi lần **mở hộp thoại mua** là một
key mới.

### 3. Proxy bằng route handler, không bằng `rewrites()`

Đích của `rewrites()` trong `next.config` bị **chốt lúc build**. Với `output: standalone`,
`next.config` không được copy vào image, nên đặt `BACKEND_URL` lúc chạy container hoàn toàn vô tác
dụng — Next vẫn gọi vào địa chỉ đã nướng sẵn từ lúc build. Triệu chứng: container báo
`ECONNREFUSED 127.0.0.1:8080` dù biến môi trường trỏ đúng `http://app:8080`.

Nên việc proxy do [`app/api/[...path]/route.ts`](src/app/api/%5B...path%5D/route.ts) lo — nó đọc
biến môi trường **tại thời điểm có request**. Cùng một image chạy được ở máy dev, trong compose, hay
trên server thật mà không phải build lại.

### 4. Đồng hồ giữ hàng tính theo mốc hết hạn, không trừ dần

[`Countdown`](src/components/Countdown.tsx) tính mốc hết hạn **một lần** rồi so với đồng hồ máy mỗi
giây, thay vì trừ dần một biến đếm. Lý do: tab chạy nền bị trình duyệt giảm nhịp `setInterval`, trừ
dần sẽ chạy chậm lại và hiện số giây còn **nhiều hơn** thực tế — khách tưởng còn thời gian trong khi
backend đã nhả hàng từ lâu.

---

## Xử lý lỗi

Backend trả một envelope lỗi duy nhất (`ApiError`), và giao diện phân nhánh theo `code` chứ không
đọc `message` — message có thể đổi, `code` thì không.
[`ApiError`](src/lib/api/client.ts) giữ nguyên `status`, `code`, `details` và `traceId`.

Vài chỗ dịch mã lỗi thành hành động cụ thể thay vì báo chung chung:

| Mã | Màn hình xử lý thế nào |
|---|---|
| `INSUFFICIENT_STOCK` | Đọc `details` (`StockShortage[]`) và chỉ ra **từng dòng** thiếu bao nhiêu |
| `RESERVATION_EXPIRED` | Chuyển sang trạng thái hết hạn kèm nút giữ hàng lại |
| `COUPON_USED_UP` | Bỏ mã, mời đặt lại — mã "hợp lệ" lúc kiểm tra vẫn có thể hết lượt lúc đặt đơn |
| `SOLD_OUT` / `FLASH_SALE_LIMIT_EXCEEDED` / `429` / `503` | Mỗi mã một câu riêng, vì đây là bốn tình huống khác hẳn nhau |

Riêng `403` được xử lý bằng cách **ẩn hẳn** mục không có quyền (STAFF không thấy doanh thu, mã giảm
giá, flash sale). Hiện ra rồi trả 403 chỉ làm người dùng đoán mò.

> `AuthGuard` và việc ẩn menu chỉ là hàng rào giao diện. Quyền thật do backend quyết định
> (`@PreAuthorize` + kiểm quyền sở hữu ở tầng service) — giấu nút mà backend không chặn thì vẫn là
> lỗ hổng.

---

## Màn hình

**Cửa hàng** — trang chủ · danh sách + lọc (danh mục, hãng, khoảng giá, còn hàng, thông số kỹ
thuật, sắp xếp, phân trang; bộ lọc nằm trong URL nên chia sẻ link ra là ra đúng kết quả) · chi tiết
sản phẩm + đánh giá · so sánh 2–3 máy · giỏ hàng · thanh toán · đơn hàng + thanh toán + viết đánh
giá · flash sale · tra cứu bảo hành · **tài khoản** (hồ sơ, đổi mật khẩu, sổ địa chỉ) · **yêu thích**
(tim trên thẻ sản phẩm) · **chuông thông báo** (polling 30 giây, chỉ khi tab đang hiển thị) · thanh
toán chọn nhanh địa chỉ từ sổ.

**Quản trị** — tổng quan (doanh thu theo ngày/tháng, bán chạy, đơn theo trạng thái) · đơn hàng +
đổi trạng thái · sản phẩm + điều chỉnh tồn kho · tồn kho sắp hết · nhập lô serial/IMEI · duyệt bảo
hành · mã giảm giá · flash sale (nạp trước Redis, đối soát) · **thống kê** (KPI so kỳ trước, xu hướng,
cơ cấu, phễu, bản đồ nhiệt thứ × giờ, thanh toán, mã giảm giá, khách hàng, đánh giá, sức khoẻ tồn
kho, xuất CSV) · **người dùng** (khoá/mở, vai trò, tạo nhân viên) · **nhật ký thao tác**.

Biểu đồ trang thống kê nằm ở `src/components/charts/` — tự vẽ, không thư viện
([ADR-0015](../docs/adr/0015-bieu-do-tu-ve.md)). Màu lấy từ biến `--viz-*` trong `globals.css`, có bộ
riêng cho dark mode; mọi biểu đồ có chế độ xem bảng.

Biểu đồ doanh thu dùng **một sắc lam duy nhất**, bậc màu chọn riêng cho nền sáng và nền tối (không
lật màu tự động), đã kiểm tương phản ≥ 3:1 trên cả hai nền. Một chuỗi số liệu thì không cần bảng
chú giải — tiêu đề đã nói đó là gì. Có nút chuyển sang **xem dạng bảng** cho ai cần con số chính
xác thay vì ước lượng theo chiều cao cột.

---

## Kiểm tra

```bash
npm run typecheck    # tsc --noEmit, strict
npm run lint         # ESLint flat config
npm run build
```

### Một lưu ý về ESLint

`eslint-config-next@16` khai peer dependency `eslint >= 9.0.0`, nhưng `eslint-plugin-react@7.37.5`
mà nó đóng gói kèm lại dùng API context đã bị **ESLint 10** bỏ (`context.getFilename`). Cài ESLint 10
là lint chết ngay khi nạp rule. Vì vậy ESLint được **ghim ở `^9`** cho tới khi upstream sửa.

Rule `react-hooks/set-state-in-effect` (mới từ Next 16) được **tắt có chủ đích** — lý do viết trong
[`eslint.config.mjs`](eslint.config.mjs). Tóm tắt: access token nằm trong localStorage nên mọi lời
gọi API buộc phải chạy phía client, không đẩy lên Server Component được; đây là rule về hiệu năng
chứ không phải tính đúng đắn.
