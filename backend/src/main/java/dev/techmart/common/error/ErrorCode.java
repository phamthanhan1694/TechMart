package dev.techmart.common.error;

import org.springframework.http.HttpStatus;

/**
 * Danh mục ma lỗi - xem docs/04-error-catalog.md.
 *
 * <p>Quy tắc chon status:
 * 401 chưa xac thuc, 403 thieu quyen theo role, 404 không thấy HOAC không thuộc user hiện tại,
 * 409 xung dot trạng thái, 422 dùng định dạng nhưng vì phạm luat nghiệp vụ, 429 vượt rate limit.
 *
 * <p>Vì sao tai nguyen của người khác trả 404 chứ không 403: trả 403 là gián tiếp xác nhận id đó
 * tồn tại, ke tan cong đó tuan từ sẽ ve được ban đó dữ liệu.
 */
public enum ErrorCode {

    // --- Auth & User ---
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "Email da duoc su dung"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Email hoac mat khau khong dung"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Token da het han"),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Token khong hop le"),
    REFRESH_TOKEN_REUSED(HttpStatus.UNAUTHORIZED, "Refresh token da bi thu hoi"),
    ACCOUNT_LOCKED(HttpStatus.FORBIDDEN, "Tai khoan da bi khoa"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Khong du quyen truy cap"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Chua dang nhap"),
    LOGIN_TEMPORARILY_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "Dang nhap sai qua nhieu lan, thu lai sau"),
    // 422 chứ không phải 401: client coi mọi 401 là "phiên hết hạn" và sẽ đăng xuất người dùng
    CURRENT_PASSWORD_INCORRECT(HttpStatus.UNPROCESSABLE_ENTITY, "Mat khau hien tai khong dung"),
    PASSWORD_UNCHANGED(HttpStatus.UNPROCESSABLE_ENTITY, "Mat khau moi phai khac mat khau hien tai"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "Khong tim thay nguoi dung"),
    CANNOT_MODIFY_SELF(HttpStatus.CONFLICT, "Khong the tu khoa hoac tu doi vai tro cua chinh minh"),
    LAST_ADMIN(HttpStatus.CONFLICT, "Phai con it nhat mot ADMIN dang hoat dong"),

    // --- Tài khoản: địa chỉ, yêu thích, thông báo ---
    ADDRESS_NOT_FOUND(HttpStatus.NOT_FOUND, "Khong tim thay dia chi"),
    ADDRESS_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "Da dat so dia chi toi da"),
    WISHLIST_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "Danh sach yeu thich da day"),
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Khong tim thay thong bao"),

    // --- Catalog ---
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "Khong tim thay san pham"),
    VARIANT_NOT_FOUND(HttpStatus.NOT_FOUND, "Khong tim thay phien ban san pham"),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "Khong tim thay danh muc"),
    BRAND_NOT_FOUND(HttpStatus.NOT_FOUND, "Khong tim thay thuong hieu"),
    SLUG_ALREADY_EXISTS(HttpStatus.CONFLICT, "Slug da ton tai"),
    SKU_ALREADY_EXISTS(HttpStatus.CONFLICT, "SKU da ton tai"),
    CATEGORY_NOT_EMPTY(HttpStatus.CONFLICT, "Danh muc van con san pham"),
    INVALID_SPEC_SCHEMA(HttpStatus.UNPROCESSABLE_ENTITY, "Thong so ky thuat khong dung dinh dang"),

    // --- Cart & Checkout ---
    CART_EMPTY(HttpStatus.UNPROCESSABLE_ENTITY, "Gio hang dang trong"),
    CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "Khong tim thay san pham trong gio"),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "San pham khong du so luong"),
    QUANTITY_EXCEEDS_LIMIT(HttpStatus.UNPROCESSABLE_ENTITY, "Vuot qua so luong toi da moi don"),
    RESERVATION_EXPIRED(HttpStatus.CONFLICT, "Phien giu hang da het han"),
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Khong tim thay phien giu hang"),

    // --- Order ---
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "Khong tim thay don hang"),
    INVALID_STATE_TRANSITION(HttpStatus.CONFLICT, "Khong the chuyen trang thai nhu vay"),
    ORDER_NOT_CANCELLABLE(HttpStatus.CONFLICT, "Don hang khong the huy o trang thai nay"),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "Don hang vua duoc nguoi khac cap nhat, thu lai"),
    REFUND_WINDOW_EXPIRED(HttpStatus.UNPROCESSABLE_ENTITY, "Da qua han yeu cau hoan tra"),

    // --- Payment ---
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Khong tim thay giao dich"),
    ORDER_ALREADY_PAID(HttpStatus.CONFLICT, "Don hang da duoc thanh toan"),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY, "So tien khong khop voi don hang"),
    PAYMENT_FAILED(HttpStatus.PAYMENT_REQUIRED, "Thanh toan that bai"),
    INVALID_WEBHOOK_SIGNATURE(HttpStatus.UNAUTHORIZED, "Chu ky webhook khong hop le"),
    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "Thieu header Idempotency-Key"),
    IDEMPOTENCY_KEY_CONFLICT(HttpStatus.CONFLICT, "Idempotency-Key da duoc dung voi noi dung khac"),

    // --- Promotion & Flash sale ---
    COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "Khong tim thay ma giam gia"),
    COUPON_EXPIRED(HttpStatus.UNPROCESSABLE_ENTITY, "Ma giam gia het hieu luc"),
    COUPON_USED_UP(HttpStatus.CONFLICT, "Ma giam gia da het luot su dung"),
    COUPON_USER_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "Ban da dung het luot cho ma nay"),
    COUPON_MIN_ORDER_NOT_MET(HttpStatus.UNPROCESSABLE_ENTITY, "Chua dat gia tri don toi thieu"),
    FLASH_SALE_NOT_STARTED(HttpStatus.CONFLICT, "Phien ban chua bat dau"),
    FLASH_SALE_ENDED(HttpStatus.CONFLICT, "Phien ban da ket thuc"),
    SOLD_OUT(HttpStatus.CONFLICT, "Da het suat"),
    FLASH_SALE_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "Vuot gioi han mua cua phien"),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Qua nhieu yeu cau, thu lai sau"),

    // --- Review & Warranty ---
    REVIEW_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_ENTITY, "Chi danh gia duoc san pham da mua va da nhan"),
    REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT, "Ban da danh gia san pham nay"),
    SERIAL_NOT_FOUND(HttpStatus.NOT_FOUND, "Khong tim thay so serial"),

    // --- Báo cáo ---
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "Khoang ngay khong hop le"),
    EXPORT_TOO_LARGE(HttpStatus.UNPROCESSABLE_ENTITY, "Qua nhieu dong de xuat, hay thu hep khoang ngay"),

    // --- He thong ---
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Du lieu gui len khong hop le"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Yeu cau sai dinh dang"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Khong tim thay tai nguyen"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Loi he thong"),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Dich vu tam thoi khong kha dung");

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
