# Danh mục mã lỗi

> Áp dụng từ ngày đầu tiên code, không phải việc dọn dẹp cuối dự án. Thêm mã lỗi vào bảng này **trước** khi ném exception tương ứng.
> Đây là loại tài liệu mà interviewer nhìn vào là biết bạn từng nghĩ về API như một sản phẩm, không chỉ như bài tập.

---

## 1. Cách hiện thực hóa

Một exception gốc, mọi exception nghiệp vụ kế thừa nó, một handler duy nhất:

```java
public abstract class BusinessException extends RuntimeException {
    private final ErrorCode code;
    private final Object details;   // null nếu không có
}

public enum ErrorCode {
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "Sản phẩm không đủ số lượng"),
    // ...
}
```

`@RestControllerAdvice` bắt `BusinessException`, dựng envelope ở `03-api-contract.md` mục 1, log ở mức `WARN` kèm `traceId`. Bắt riêng `Exception` cho `500`, log `ERROR` kèm stack trace **vào log, không vào response**.

**Nguyên tắc chọn HTTP status:**

| Status | Dùng khi |
|---|---|
| `400` | Request sai định dạng, thiếu field, sai kiểu |
| `401` | Chưa xác thực hoặc token hỏng/hết hạn |
| `403` | Đã xác thực nhưng không đủ quyền theo role |
| `404` | Không tìm thấy, **hoặc** tìm thấy nhưng không thuộc về user hiện tại |
| `409` | Xung đột trạng thái: hết hàng, sai luồng trạng thái, hết lượt coupon |
| `422` | Đúng định dạng nhưng vi phạm luật nghiệp vụ |
| `429` | Vượt rate limit |

> **Vì sao tài nguyên của người khác trả `404` chứ không `403`?** Trả `403` là gián tiếp xác nhận id đó có tồn tại — kẻ tấn công dò tuần tự sẽ vẽ được bản đồ dữ liệu. Chi tiết này đáng nói khi được hỏi về IDOR.

---

## 2. Auth & User

| Code | HTTP | Khi nào | Ghi chú |
|---|---|---|---|
| `EMAIL_ALREADY_EXISTS` | 409 | Đăng ký với email đã có | |
| `INVALID_CREDENTIALS` | 401 | Sai email **hoặc** sai mật khẩu | **Cùng một mã cho cả hai** — không tiết lộ email nào tồn tại |
| `TOKEN_EXPIRED` | 401 | Access token hết hạn | Client tự gọi `/refresh` |
| `TOKEN_INVALID` | 401 | Chữ ký sai / token hỏng | |
| `REFRESH_TOKEN_REUSED` | 401 | Refresh token đã dùng rồi lại dùng | **Thu hồi cả chuỗi token của user** — dấu hiệu token bị đánh cắp |
| `ACCOUNT_LOCKED` | 403 | `user.status = LOCKED` | |
| `ACCESS_DENIED` | 403 | Không đủ role | |

## 3. Catalog

| Code | HTTP | Khi nào |
|---|---|---|
| `PRODUCT_NOT_FOUND` | 404 | Không tồn tại hoặc đã soft delete |
| `VARIANT_NOT_FOUND` | 404 | |
| `CATEGORY_NOT_FOUND` | 404 | |
| `SLUG_ALREADY_EXISTS` | 409 | Trùng slug |
| `SKU_ALREADY_EXISTS` | 409 | Trùng SKU |
| `CATEGORY_NOT_EMPTY` | 409 | Xóa danh mục còn sản phẩm |
| `INVALID_SPEC_SCHEMA` | 422 | Spec JSONB sai schema của danh mục |

## 4. Cart & Checkout

| Code | HTTP | Khi nào | `details` |
|---|---|---|---|
| `CART_EMPTY` | 422 | Checkout với giỏ rỗng | |
| `CART_ITEM_NOT_FOUND` | 404 | Item không tồn tại hoặc không thuộc giỏ user hiện tại | |
| `INSUFFICIENT_STOCK` | 409 | `available < quantity` | **Có** — liệt kê từng variant: `{variantId, sku, requested, available}` |
| `QUANTITY_EXCEEDS_LIMIT` | 422 | Vượt giới hạn mua/đơn | |
| `RESERVATION_EXPIRED` | 409 | Đặt hàng sau khi phiên giữ hàng hết hạn | Client cho khách checkout lại |
| `RESERVATION_NOT_FOUND` | 404 | `sessionId` sai hoặc đã bị hủy | |
| `RESERVATION_NOT_OWNED` | 404 | Session của user khác | Trả `404`, không phải `403` |

`INSUFFICIENT_STOCK` là mã quan trọng nhất tài liệu này — nó xuất hiện ở ba chỗ (thêm giỏ, giữ hàng, đặt hàng) và **phải có `details`**. Trả chung chung "hết hàng" khi khách mua 5 món là trải nghiệm tệ.

## 5. Order

| Code | HTTP | Khi nào |
|---|---|---|
| `ORDER_NOT_FOUND` | 404 | Không tồn tại, hoặc thuộc user khác |
| `INVALID_STATE_TRANSITION` | 409 | Chuyển trạng thái không có trong bảng ở `02-luong-nghiep-vu.md` |
| `ORDER_ALREADY_CANCELLED` | — | **Không phải lỗi.** Hủy đơn đã hủy trả `200` — xem `02-luong-nghiep-vu.md` mục 6 |
| `ORDER_NOT_CANCELLABLE` | 409 | Đơn đã `SHIPPED` trở đi, customer không được hủy |
| `CONCURRENT_MODIFICATION` | 409 | `OptimisticLockException` — hai người cùng đổi trạng thái một đơn |
| `REFUND_WINDOW_EXPIRED` | 422 | Yêu cầu hoàn quá 7 ngày sau `DELIVERED` |

`ORDER_ALREADY_CANCELLED` để trong bảng có chủ đích: nhắc rằng đây **không** phải trường hợp lỗi. Đó là nơi lỗi hoàn kho hai lần sinh ra.

## 6. Payment

| Code | HTTP | Khi nào |
|---|---|---|
| `PAYMENT_NOT_FOUND` | 404 | |
| `ORDER_ALREADY_PAID` | 409 | Thanh toán đơn đã trả tiền |
| `PAYMENT_AMOUNT_MISMATCH` | 422 | Số tiền webhook khác `order.total_amount` — **luôn kiểm, không tin webhook** |
| `PAYMENT_FAILED` | 402 | Gateway từ chối |
| `INVALID_WEBHOOK_SIGNATURE` | 401 | HMAC không khớp |
| `IDEMPOTENCY_KEY_REQUIRED` | 400 | Thiếu header ở endpoint bắt buộc |
| `IDEMPOTENCY_KEY_CONFLICT` | 409 | Cùng key nhưng payload khác — dấu hiệu client lỗi |

`IDEMPOTENCY_KEY_CONFLICT` là chi tiết ít người làm: cùng key mà payload khác thì **không** được trả response cũ, vì client đang định làm việc khác. Chuẩn Stripe làm đúng như vậy — dẫn được nguồn này khi phỏng vấn là điểm cộng.

## 7. Promotion & Flash Sale

| Code | HTTP | Khi nào |
|---|---|---|
| `COUPON_NOT_FOUND` | 404 | |
| `COUPON_EXPIRED` | 422 | Ngoài `starts_at`–`ends_at` |
| `COUPON_USED_UP` | 409 | `used_count >= usage_limit` |
| `COUPON_USER_LIMIT_EXCEEDED` | 409 | Vượt `per_user_limit` |
| `COUPON_MIN_ORDER_NOT_MET` | 422 | `subtotal < min_order_amount` |
| `FLASH_SALE_NOT_STARTED` | 409 | Mua trước giờ mở bán |
| `FLASH_SALE_ENDED` | 409 | |
| `SOLD_OUT` | 409 | Lua script trả `-1` |
| `FLASH_SALE_LIMIT_EXCEEDED` | 409 | Vượt `per_user_limit`, Lua trả `-2` |
| `RATE_LIMIT_EXCEEDED` | 429 | Kèm `Retry-After` |

## 8. Hệ thống

| Code | HTTP | Khi nào | Ghi chú |
|---|---|---|---|
| `VALIDATION_FAILED` | 400 | `@Valid` thất bại | `details` liệt kê `{field, message}` từng field |
| `MALFORMED_REQUEST` | 400 | JSON hỏng, sai kiểu | |
| `RESOURCE_NOT_FOUND` | 404 | Fallback | |
| `INTERNAL_ERROR` | 500 | Ngoại lệ không lường trước | **Chỉ trả `traceId`**, tuyệt đối không stack trace |
| `SERVICE_UNAVAILABLE` | 503 | DB/Redis không kết nối được | |

---

## 9. Suy giảm chức năng khi Redis chết

Redis là hạ tầng phụ, **service không được sập theo nó**. Bảng này là câu trả lời trực tiếp cho câu hỏi phỏng vấn *"Redis chết thì hệ thống của bạn thế nào?"*:

| Chức năng | Khi Redis chết | Vì sao chấp nhận được |
|---|---|---|
| Cache sản phẩm | Truy vấn thẳng DB, chậm hơn | Chỉ mất tốc độ |
| Rate limit | **Fail-open** (cho qua) + log cảnh báo | Chặn hết người dùng thật tệ hơn là tạm mất chống bot |
| Reservation | Vẫn đúng — DB là nguồn sự thật, job `@Scheduled` vẫn nhả hàng | Xem `02-luong-nghiep-vu.md` mục 2 |
| Idempotency | Vẫn đúng — ràng buộc `UNIQUE` ở DB đảm bảo | Redis chỉ là lớp chặn nhanh |
| Flash sale | **Fail-closed** — trả `503 SERVICE_UNAVAILABLE` | Không có Redis thì không đảm bảo được không oversell. Từ chối bán an toàn hơn bán quá |

> Chỗ này chính là câu trả lời hay nhất trong cả buổi phỏng vấn: **rate limit fail-open còn flash sale fail-closed**. Cùng một hạ tầng chết, hai chức năng ứng xử ngược nhau, và cả hai đều đúng — vì hậu quả của việc sai khác nhau. Chuẩn bị kỹ câu này.
