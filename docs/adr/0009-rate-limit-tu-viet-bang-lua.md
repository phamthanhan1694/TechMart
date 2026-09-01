# ADR-0009: Rate limit tự viết bằng Lua trên Redis, không dùng Bucket4j

- **Trạng thái:** Chấp nhận
- **Ngày:** 2026-09-01
- **Liên quan:** ADR-0010, `04-error-catalog.md` §9

## Bối cảnh

Cần chặn dò mật khẩu ở `/auth/login` và chặn bot ở endpoint flash sale. Kế hoạch ban đầu ghi "dùng Bucket4j + Redis".

Nhưng đến lúc làm thì flash sale **đã** cần đúng cơ chế Redis + Lua nguyên tử rồi (ADR-0010). Câu hỏi thành: thêm một thư viện để làm việc mà cơ chế sẵn có đã làm được, hay viết thêm 40 dòng Lua?

## Các phương án đã cân nhắc

| Phương án | Ưu | Nhược |
|---|---|---|
| Bộ đếm trong bộ nhớ JVM | Không cần gì | **Sai ngay khi có instance thứ hai** — mỗi instance đếm riêng, giới hạn bị nhân đôi. Cùng lý do khiến `synchronized` vô dụng khi chống oversell |
| Bucket4j + Redis | Thư viện đã kiểm chứng, là từ khóa nhà tuyển dụng nhận ra | Thêm phụ thuộc; phải nối `LettuceConnectionFactory` của Spring sang `RedisClient` thô của Bucket4j; thêm một vòng đời phải quản lý |
| **Token bucket viết bằng Lua** | Dùng lại đúng cơ chế flash sale đã có; ~40 dòng; không phụ thuộc | Tự viết thì tự chịu trách nhiệm đúng sai |
| Rate limit ở tầng reverse proxy (nginx) | Không đụng vào ứng dụng | Không phân biệt được theo user đã đăng nhập; dự án chạy local không có proxy |

## Quyết định

Token bucket viết bằng Lua (`redis/rate-limit.lua`), gọi qua `RedisScript<List>`. Trả về `{allowed, remaining, retryAfterMillis}` để dựng header `X-RateLimit-*` và `Retry-After`.

Lý do chính không phải "tự viết vui hơn" mà là **một khái niệm cho cả hai tính năng**: người đọc code chỉ cần hiểu "Redis chạy Lua nguyên tử" một lần là hiểu cả flash sale lẫn rate limit. Thêm Bucket4j nghĩa là hai cơ chế khác nhau cho cùng một ý tưởng.

Hai tầng ở flash sale: theo **user** và theo **IP**. Cần cả hai vì một user đổi được IP, còn một IP sau NAT có nhiều user thật.

**Fail-open khi Redis chết** — chặn nhầm toàn bộ người dùng thật tệ hơn nhiều so với tạm mất khả năng chống bot. Ngược với flash sale (fail-closed, ADR-0010).

## Đánh đổi chấp nhận

- Tự chịu trách nhiệm về tính đúng của thuật toán. Token bucket đơn giản, nhưng vẫn là code mình phải bảo trì.
- Mất "Bucket4j" như một từ khóa trên CV. Đổi lại giải thích được *vì sao* không dùng — thường có giá trị hơn.
- Giới hạn hiện cấu hình cứng trong `RateLimitFilter.RULES`, đổi phải build lại.

## Khi nào nên đổi ý

- Khi cần các thuật toán phức tạp hơn (nhiều tầng bucket lồng nhau, giới hạn theo gói cước) — lúc đó Bucket4j đáng giá.
- Khi cần đổi giới hạn lúc đang chạy — cần đưa cấu hình ra ngoài, và thư viện có sẵn cơ chế đó.
- Khi có reverse proxy đứng trước và phần lớn tải là request chưa đăng nhập — chặn ở proxy rẻ hơn nhiều.

## Kiểm chứng

Thử thật: bắn liên tiếp vào `/auth/login`, vượt ngưỡng thì nhận `429` kèm `Retry-After` và `X-RateLimit-Remaining: 0`.
Metric `techmart_ratelimit_rejected_total` đếm số request bị chặn.
`flashsale-race.sh` báo rõ khi chính rate limit chặn bớt số token lấy được — đó cũng là bằng chứng nó hoạt động.
