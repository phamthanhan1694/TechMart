# Checklist bảo mật

> Rà soát cuối tuần 5, nhưng phần lớn phải làm đúng ngay từ lúc code — vá sau tốn gấp ba.
> Mỗi mục có cột **Hỏi phỏng vấn** đánh dấu những chỗ hay bị hỏi nhất.

---

## 1. Xác thực & phiên đăng nhập

| ✔ | Mục | Chi tiết | Hỏi |
|---|---|---|---|
| ☐ | Mật khẩu băm BCrypt | `BCryptPasswordEncoder` với strength 10–12. Không MD5/SHA, không tự nghĩ ra cách băm | ★ |
| ☐ | Không bao giờ log mật khẩu | Kể cả ở mức DEBUG, kể cả trong DTO `toString()` | |
| ☐ | Access token ngắn hạn | 15 phút | ★ |
| ☐ | Refresh token có xoay vòng | Mỗi lần refresh cấp cặp mới, thu hồi cái cũ | ★★ |
| ☐ | Phát hiện tái sử dụng refresh token | Dùng lại token đã thu hồi → **thu hồi cả chuỗi của user đó** | ★★★ |
| ☐ | Refresh token lưu dạng **hash** trong DB | DB rò rỉ thì token vẫn không dùng được | ★★ |
| ☐ | Logout thu hồi thật | Xóa/đánh dấu revoke trong DB, không chỉ xóa ở client | ★ |
| ☐ | Secret JWT lấy từ biến môi trường | Không hardcode, không commit. Tối thiểu 256 bit | ★★ |
| ☐ | Thuật toán JWT cố định | Chỉ chấp nhận `HS256`, từ chối `alg: none` và từ chối token tự khai thuật toán khác | ★★ |
| ☐ | Đăng nhập sai không tiết lộ email tồn tại | Sai email và sai mật khẩu trả cùng `401 INVALID_CREDENTIALS` | ★ |
| ☐ | Rate limit `/login` | 5 lần/phút/IP | ★ |

> **Câu hỏi hay gặp nhất:** *"Vì sao cần refresh token, sao không cho access token sống 7 ngày luôn?"*
> Trả lời: access token là bearer, không thu hồi được — ai cầm được là dùng được cho tới khi hết hạn. Rút ngắn tuổi thọ là cách duy nhất giới hạn thiệt hại. Refresh token thì tra được DB nên thu hồi được ngay. Rotation cộng thêm khả năng **phát hiện** token bị đánh cắp: token cũ bị dùng lại nghĩa là có hai bên đang giữ, thu hồi cả chuỗi.

---

## 2. Phân quyền & IDOR

| ✔ | Mục | Chi tiết | Hỏi |
|---|---|---|---|
| ☐ | Không nhận `userId` từ client | Mọi thao tác lấy user từ `SecurityContext` | ★★★ |
| ☐ | Kiểm quyền sở hữu ở **tầng service** | Không chỉ `@PreAuthorize` theo role — role đúng vẫn có thể xem đơn người khác | ★★★ |
| ☐ | Tài nguyên người khác trả `404` | Trả `403` là xác nhận id đó tồn tại | ★★ |
| ☐ | Endpoint admin có `@PreAuthorize` | Rà **từng** endpoint, không dựa vào quy ước URL | ★ |
| ☐ | Mặc định là từ chối | `SecurityFilterChain` cấu hình `anyRequest().authenticated()`, chỉ mở rõ ràng những gì Public | ★★ |
| ☐ | Có test IDOR | `updateCartItem_ofAnotherUser_returns404` | ★★ |

Chỗ này là lỗ hổng số một của mọi e-commerce sinh viên: `@PreAuthorize("hasRole('CUSTOMER')")` đúng role nhưng không kiểm đơn hàng có phải của người đó không. Đổi số trong URL là xem được đơn của người lạ.

---

## 3. Đầu vào & dữ liệu

| ✔ | Mục | Chi tiết | Hỏi |
|---|---|---|---|
| ☐ | `@Valid` trên mọi `@RequestBody` | Kèm `@NotBlank`, `@Size`, `@Min`, `@Email`, `@Positive` | ★ |
| ☐ | Chống mass assignment | DTO riêng cho request; **không** để client gửi `role`, `status`, `price` vào entity qua binding | ★★★ |
| ☐ | Không truyền entity vào `@RequestBody` | | ★★ |
| ☐ | Giới hạn `size` phân trang | Tối đa 100 | ★ |
| ☐ | Native query dùng tham số ràng buộc | Truy vấn JSONB dễ bị viết bằng nối chuỗi — **kiểm kỹ chỗ này** | ★★★ |
| ☐ | Tên cột `sort` phải whitelist | `sort=price,asc` — nếu ghép thẳng vào SQL là lỗ hổng injection | ★★ |
| ☐ | Số tiền luôn tính lại ở server | Không bao giờ tin `total` client gửi lên | ★★★ |
| ☐ | Giá coupon tính lại ở server | `validate` chỉ để hiển thị, không phải cam kết | ★★ |
| ☐ | Upload ảnh (nếu có) kiểm MIME + kích thước | Kiểm nội dung thật, không chỉ đuôi file | ★ |

> Hai dòng ★★★ về tiền là chỗ đáng nói nhất: **mọi con số tiền trong request đều là gợi ý, server tính lại từ đầu.** Nếu client gửi `total: 0` mà server dùng luôn thì cả hệ thống thanh toán vô nghĩa. Rất nhiều project portfolio dính lỗi này.

Riêng JSONB: bộ lọc `spec.ram=16GB` biến thành truy vấn động. Viết đúng cách:

```java
@Query(value = "SELECT * FROM products WHERE spec @> CAST(:filter AS jsonb) AND deleted_at IS NULL",
       nativeQuery = true)
List<Product> findBySpec(@Param("filter") String filterJson);
```

Dựng `filterJson` bằng thư viện JSON từ `Map<String,String>` đã whitelist key — **không** nối chuỗi tay.

---

## 4. Rò rỉ thông tin

| ✔ | Mục | Chi tiết | Hỏi |
|---|---|---|---|
| ☐ | Không trả stack trace ra response | `500` chỉ trả `code` + `traceId` | ★★ |
| ☐ | Tắt `server.error.include-*` ở prod | `include-stacktrace=never`, `include-message=never` | ★ |
| ☐ | Không trả `password_hash` trong DTO nào | | ★ |
| ☐ | Không log token / mật khẩu / số thẻ | Có filter làm sạch log | ★★ |
| ☐ | Swagger ở prod: cân nhắc | Với project portfolio thì **mở** (đó là giao diện demo). Ghi rõ trong README là quyết định có ý thức | ★ |
| ☐ | Actuator chỉ mở `health` + `prometheus` | `/actuator/env`, `/actuator/heapdump` rò rỉ secret | ★★★ |
| ☐ | Mã đơn không đoán được | Dùng `code` chứ không phơi `id` tuần tự | ★ |
| ☐ | Thông báo lỗi không tiết lộ nội bộ | Không trả tên bảng, tên cột, câu SQL | ★ |

`/actuator/env` mở công khai là lỗ hổng thật và nghiêm trọng — nó in ra toàn bộ biến môi trường, gồm cả chuỗi kết nối DB và secret JWT. Đây là mục đáng kiểm hai lần trước khi deploy.

---

## 5. Hạ tầng & cấu hình

| ✔ | Mục | Chi tiết | Hỏi |
|---|---|---|---|
| ☐ | Không secret nào trong repo | Rà bằng `git log -p \| grep -i -E "password\|secret\|token"` | ★★ |
| ☐ | `.env` trong `.gitignore`, có `.env.example` | | ★ |
| ☐ | CORS whitelist cụ thể | Không `allowedOrigins("*")` kèm `allowCredentials(true)` — Spring sẽ ném lỗi, và đó là điều đúng | ★★ |
| ☐ | HTTPS ở môi trường deploy | Render/Railway cấp sẵn | ★ |
| ☐ | Security headers | `X-Content-Type-Options`, `X-Frame-Options`, HSTS | ★ |
| ☐ | Rate limit toàn cục | Ngoài các endpoint nhạy cảm, có một mức trần chung theo IP | ★★ |
| ☐ | Rate limit **fail-open** khi Redis chết | Chặn hết người dùng thật tệ hơn tạm mất chống bot — xem `04-error-catalog.md` mục 9 | ★★★ |
| ☐ | Flash sale **fail-closed** khi Redis chết | Không đảm bảo được thì từ chối bán | ★★★ |
| ☐ | Dependency scan | `mvn dependency-check` hoặc bật Dependabot trên GitHub | ★★ |
| ☐ | Docker chạy user không phải root | `USER appuser` trong Dockerfile | ★★ |
| ☐ | Multi-stage build | Image cuối không chứa Maven, không chứa source | ★ |
| ☐ | Webhook thanh toán xác thực HMAC | Mock gateway cũng ký — làm đúng như thật | ★★★ |

---

## 6. Logic nghiệp vụ (dễ bị bỏ qua nhất)

| ✔ | Mục | Vì sao quan trọng |
|---|---|---|
| ☐ | Không tin số tiền từ webhook | Kiểm `amount == order.total_amount` trước khi đánh dấu `PAID`. Thiếu bước này, ai gửi được webhook giả là mua hàng miễn phí |
| ☐ | Không tin `quantity` âm | `CHECK (quantity >= 1)` ở DB. Số lượng âm = tiền âm = hoàn tiền vô hạn |
| ☐ | Coupon trừ lượt trong transaction có khóa | Không thì 1000 người dùng chung một mã giới hạn 1 lượt |
| ☐ | Chỉ review khi đã mua và đã nhận | `order_item` thuộc user + đơn `DELIVERED` + `UNIQUE(order_item_id)` |
| ☐ | Hủy đơn idempotent | Hủy hai lần không hoàn kho hai lần |
| ☐ | Giới hạn số lượng mua mỗi đơn | Chặn người quét sạch kho bằng một request |
| ☐ | Không cho khách tự đổi trạng thái đơn sang `PAID` | Chỉ webhook thanh toán và admin được |

Dòng đầu và dòng cuối là hai lỗi "mua hàng miễn phí" kinh điển. Nếu chỉ đủ thời gian kiểm hai mục trong cả tài liệu này, kiểm hai mục đó.

---

## 7. Ba câu trả lời chuẩn bị sẵn

**"Bạn xử lý bảo mật gì trong project?"** — Đừng liệt kê. Kể ba tầng: (1) xác thực — JWT ngắn hạn + refresh rotation có phát hiện tái sử dụng; (2) phân quyền — kiểm quyền sở hữu ở tầng service chứ không chỉ role, có test IDOR chứng minh; (3) tính toàn vẹn nghiệp vụ — mọi số tiền tính lại ở server, webhook xác thực HMAC và đối chiếu số tiền.

**"Lỗ hổng nào bạn từng suýt để lọt?"** — Câu này rất hay được hỏi để đo mức độ trung thực. Có câu trả lời thật thì kể; nếu chưa, dùng chính mục 3: ban đầu bộ lọc JSONB dựng bằng nối chuỗi, sau chuyển sang tham số ràng buộc + whitelist key.

**"Nếu có thêm một tuần, bạn làm gì cho bảo mật?"** — Audit log cho hành động admin, khóa tài khoản sau N lần đăng nhập sai, xoay vòng secret, và quét dependency tự động trong CI.
