package dev.techmart.common.error;

import org.springframework.http.HttpStatus;

/**
 * Danh mục mã lỗi - xem docs/04-error-catalog.md.
 *
 * <p>Quy tắc chọn status:
 * 401 chưa xác thực, 403 thiếu quyền theo role, 404 không thấy HOẶC không thuộc user hiện tại,
 * 409 xung đột trạng thái, 422 đúng định dạng nhưng vi phạm luật nghiệp vụ, 429 vượt rate limit.
 *
 * <p>Vì sao tài nguyên của người khác trả 404 chứ không 403: trả 403 là gián tiếp xác nhận id đó
 * tồn tại, kẻ tấn công dò tuần tự sẽ vẽ được bản đồ dữ liệu.
 */
public enum ErrorCode {

    // --- Auth & User ---
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "Email đã được sử dụng"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Token đã hết hạn"),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Token không hợp lệ"),
    REFRESH_TOKEN_REUSED(HttpStatus.UNAUTHORIZED, "Refresh token đã bị thu hồi"),
    ACCOUNT_LOCKED(HttpStatus.FORBIDDEN, "Tài khoản đã bị khoá"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Không đủ quyền truy cập"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập"),
    LOGIN_TEMPORARILY_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "Đăng nhập sai quá nhiều lần, thử lại sau"),
    // 422 chứ không phải 401: client coi mọi 401 là "phiên hết hạn" và sẽ đăng xuất người dùng
    CURRENT_PASSWORD_INCORRECT(HttpStatus.UNPROCESSABLE_CONTENT, "Mật khẩu hiện tại không đúng"),
    PASSWORD_UNCHANGED(HttpStatus.UNPROCESSABLE_CONTENT, "Mật khẩu mới phải khác mật khẩu hiện tại"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"),
    CANNOT_MODIFY_SELF(HttpStatus.CONFLICT, "Không thể tự khoá hoặc tự đổi vai trò của chính mình"),
    LAST_ADMIN(HttpStatus.CONFLICT, "Phải còn ít nhất một ADMIN đang hoạt động"),

    // --- Tài khoản: địa chỉ, yêu thích, thông báo ---
    ADDRESS_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy địa chỉ"),
    ADDRESS_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_CONTENT, "Đã đạt số địa chỉ tối đa"),
    WISHLIST_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_CONTENT, "Danh sách yêu thích đã đầy"),
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy thông báo"),

    // --- Catalog ---
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm"),
    VARIANT_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy phiên bản sản phẩm"),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy danh mục"),
    BRAND_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy thương hiệu"),
    SLUG_ALREADY_EXISTS(HttpStatus.CONFLICT, "Slug đã tồn tại"),
    SKU_ALREADY_EXISTS(HttpStatus.CONFLICT, "SKU đã tồn tại"),
    CATEGORY_NOT_EMPTY(HttpStatus.CONFLICT, "Danh mục vẫn còn sản phẩm"),
    INVALID_SPEC_SCHEMA(HttpStatus.UNPROCESSABLE_CONTENT, "Thông số kỹ thuật không đúng định dạng"),

    // --- Cart & Checkout ---
    CART_EMPTY(HttpStatus.UNPROCESSABLE_CONTENT, "Giỏ hàng đang trống"),
    CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm trong giỏ"),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "Sản phẩm không đủ số lượng"),
    QUANTITY_EXCEEDS_LIMIT(HttpStatus.UNPROCESSABLE_CONTENT, "Vượt quá số lượng tối đa mỗi đơn"),
    RESERVATION_EXPIRED(HttpStatus.CONFLICT, "Phiên giữ hàng đã hết hạn"),
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy phiên giữ hàng"),

    // --- Order ---
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng"),
    INVALID_STATE_TRANSITION(HttpStatus.CONFLICT, "Không thể chuyển trạng thái như vậy"),
    ORDER_NOT_CANCELLABLE(HttpStatus.CONFLICT, "Đơn hàng không thể huỷ ở trạng thái này"),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "Đơn hàng vừa được người khác cập nhật, thử lại"),
    REFUND_WINDOW_EXPIRED(HttpStatus.UNPROCESSABLE_CONTENT, "Đã quá hạn yêu cầu hoàn trả"),

    // --- Payment ---
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy giao dịch"),
    ORDER_ALREADY_PAID(HttpStatus.CONFLICT, "Đơn hàng đã được thanh toán"),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.UNPROCESSABLE_CONTENT, "Số tiền không khớp với đơn hàng"),
    PAYMENT_FAILED(HttpStatus.PAYMENT_REQUIRED, "Thanh toán thất bại"),
    INVALID_WEBHOOK_SIGNATURE(HttpStatus.UNAUTHORIZED, "Chữ ký webhook không hợp lệ"),
    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "Thiếu header Idempotency-Key"),
    IDEMPOTENCY_KEY_CONFLICT(HttpStatus.CONFLICT, "Idempotency-Key đã được dùng với nội dung khác"),

    // --- Promotion & Flash sale ---
    COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy mã giảm giá"),
    COUPON_EXPIRED(HttpStatus.UNPROCESSABLE_CONTENT, "Mã giảm giá hết hiệu lực"),
    COUPON_USED_UP(HttpStatus.CONFLICT, "Mã giảm giá đã hết lượt sử dụng"),
    COUPON_USER_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "Bạn đã dùng hết lượt cho mã này"),
    COUPON_MIN_ORDER_NOT_MET(HttpStatus.UNPROCESSABLE_CONTENT, "Chưa đạt giá trị đơn tối thiểu"),
    FLASH_SALE_NOT_STARTED(HttpStatus.CONFLICT, "Phiên bán chưa bắt đầu"),
    FLASH_SALE_ENDED(HttpStatus.CONFLICT, "Phiên bán đã kết thúc"),
    SOLD_OUT(HttpStatus.CONFLICT, "Đã hết suất"),
    FLASH_SALE_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "Vượt giới hạn mua của phiên"),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Quá nhiều yêu cầu, thử lại sau"),

    // --- Review & Warranty ---
    REVIEW_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_CONTENT, "Chỉ đánh giá được sản phẩm đã mua và đã nhận"),
    REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT, "Bạn đã đánh giá sản phẩm này"),
    SERIAL_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy số serial"),

    // --- Báo cáo ---
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "Khoảng ngày không hợp lệ"),
    EXPORT_TOO_LARGE(HttpStatus.UNPROCESSABLE_CONTENT, "Quá nhiều dòng để xuất, hãy thu hẹp khoảng ngày"),

    // --- Hệ thống ---
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Dữ liệu gửi lên không hợp lệ"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Yêu cầu sai định dạng"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy tài nguyên"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi hệ thống"),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Dịch vụ tạm thời không khả dụng");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
