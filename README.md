# TechMart

Ứng dụng thương mại điện tử cho thiết bị điện tử, gồm giao diện cửa hàng và trang quản trị. Backend tổ chức theo modular monolith, tập trung vào quản lý tồn kho, xử lý đơn hàng và tính nhất quán khi có giao dịch đồng thời.

## Công nghệ

- **Backend:** Java 21, Spring Boot 4.1, Spring Security, JPA, Flyway.
- **Frontend:** Next.js 16, React 19, TypeScript, Tailwind CSS 4.
- **Hạ tầng:** PostgreSQL 16, Redis 7, Docker Compose; kiểm thử với JUnit và Testcontainers.

## Chức năng

- Danh mục sản phẩm, tìm kiếm, giỏ hàng, địa chỉ giao hàng và danh sách yêu thích.
- Giữ hàng có thời hạn, đặt hàng, mã giảm giá, flash sale và bảo hành theo serial.
- Xác thực JWT, phân quyền khách hàng, nhân viên và quản trị viên.
- Quản trị sản phẩm, tồn kho, đơn hàng, người dùng; thống kê và nhật ký thao tác.

Tồn kho được kiểm soát bằng khóa dữ liệu và ràng buộc PostgreSQL. Redis đảm nhiệm cache, rate limiting và bộ đếm flash sale. Luồng đặt hàng và thanh toán hỗ trợ idempotency; thanh toán hiện sử dụng cổng giả lập với webhook ký HMAC.

## Cấu trúc

- `backend/`: REST API, migration cơ sở dữ liệu và bộ kiểm thử.
- `frontend/`: giao diện cửa hàng và quản trị.
- `loadtest/`: kịch bản k6 và SQL chuẩn bị dữ liệu kiểm thử tải.
- `observability/`: cấu hình Prometheus và dashboard Grafana.
- `.github/workflows/`: kiểm thử, kiểm tra frontend và build Docker image.

## Chạy dự án

Yêu cầu Docker và Docker Compose. Từ thư mục gốc:

```bash
cp .env.example .env
docker compose up -d --build
```

- Cửa hàng: http://localhost:3000
- Swagger UI: http://localhost:8080/swagger-ui.html
- Health check: http://localhost:8080/actuator/health

Cấu hình mặc định bật dữ liệu demo. Tài khoản quản trị: `admin@techmart.dev` / `Admin@123`; khách hàng: `customer@techmart.dev` / `Customer@123`. Khi triển khai thực tế, tắt seed và thay các mật khẩu, secret mặc định.


Khởi động Prometheus và Grafana:

```bash
docker compose --profile observability up -d
```

Prometheus: http://localhost:9090. Grafana: http://localhost:3001 (tài khoản mặc định `admin` / `admin`).

## Kiểm thử

Yêu cầu JDK 21, Maven, Node.js 24 và Docker đang chạy để Testcontainers khởi tạo dịch vụ phụ thuộc.

```bash
mvn -f backend/pom.xml verify
npm --prefix frontend ci
npm --prefix frontend run lint
npm --prefix frontend run typecheck
npm --prefix frontend run build
```
