# ADR-0006: Refresh token xoay vòng, lưu hash, phát hiện tái sử dụng

- **Trạng thái:** Chấp nhận
- **Ngày:** 2026-09-01
- **Liên quan:** `07-security-checklist.md` §1

## Bối cảnh

JWT là bearer token: ai cầm được là dùng được, và **không thu hồi được** cho tới khi hết hạn. Nếu access token sống 7 ngày thì một token bị lộ là 7 ngày kẻ tấn công tự do. Nếu sống 15 phút thì người dùng phải đăng nhập lại 4 lần mỗi giờ.

Refresh token giải mâu thuẫn đó, nhưng chỉ khi nó **thu hồi được** — mà muốn thu hồi thì phải tra cơ sở dữ liệu.

## Các phương án đã cân nhắc

| Phương án | Ưu | Nhược |
|---|---|---|
| Chỉ access token sống dài | Đơn giản | Không thu hồi được. Lộ token = mất tài khoản tới khi hết hạn |
| Refresh token cũng là JWT | Không cần bảng | Vẫn không thu hồi được — mất hết lợi ích, chỉ thêm bề mặt tấn công |
| **Chuỗi ngẫu nhiên 256 bit, lưu hash trong DB, xoay vòng mỗi lần dùng** | Thu hồi được; DB rò rỉ vẫn không dùng được token; phát hiện được token bị đánh cắp | Mỗi lần refresh tốn một truy vấn DB |
| Session phía server hoàn toàn | Kiểm soát tuyệt đối | Mất tính stateless của API |

## Quyết định

- **Access token:** JWT HS256, sống 15 phút, chỉ chấp nhận đúng thuật toán và đúng issuer.
- **Refresh token:** KHÔNG phải JWT — chuỗi ngẫu nhiên 256 bit từ `SecureRandom`, sống 7 ngày. DB chỉ lưu **SHA-256 hash**, giá trị thô chỉ trả về cho client đúng một lần.
- **Xoay vòng:** mỗi lần refresh cấp cặp mới và thu hồi cặp cũ, ghi `replaced_by` tạo thành chuỗi.
- **Phát hiện tái sử dụng:** một token đã bị thu hồi mà vẫn được gửi lên nghĩa là **hai bên đang giữ cùng một token** — gần như chắc chắn đã bị đánh cắp. Khi đó thu hồi **toàn bộ** chuỗi của user, buộc đăng nhập lại.

Dùng SHA-256 chứ không BCrypt cho refresh token: token đã là ngẫu nhiên 256 bit, không có gì để dò, nên làm chậm hàm băm chỉ tốn CPU vô ích. BCrypt cần cho mật khẩu vì mật khẩu do người đặt và có entropy thấp.

## Đánh đổi chấp nhận

- Mỗi lần refresh tốn một truy vấn DB. Đổi lại thu hồi được — chính là thứ ta cần.
- Bảng `refresh_tokens` lớn dần, cần job dọn token hết hạn (chưa làm).
- Người dùng mở nhiều tab có thể gặp race: hai tab cùng refresh, tab chậm hơn bị coi là tái sử dụng và bị đăng xuất. Chưa xử lý.

## Khi nào nên đổi ý

- Khi cần đăng nhập một lần cho nhiều ứng dụng (SSO) — chuyển sang OAuth2 / OIDC với một identity provider thật, đừng tự viết.
- Khi race đa tab ở trên gây phiền thật — giải bằng cửa sổ ân hạn vài giây cho token vừa bị thay.

## Kiểm chứng

- `refresh_rotatesToken_oldTokenBecomesInvalid`
- `refresh_reusedToken_revokesEntireChain` — test bảo mật quan trọng nhất của module
- `login_wrongPassword_returns401_withSameCodeAsUnknownEmail`
