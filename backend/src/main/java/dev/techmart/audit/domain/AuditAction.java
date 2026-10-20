package dev.techmart.audit.domain;

/**
 * Hành động được ghi nhật ký. Dùng enum thay vì chuỗi tự do: lọc theo hành động ở màn hình
 * admin chỉ có nghĩa khi tập giá trị đóng và không có hai cách viết cho cùng một việc.
 */
public enum AuditAction {
    ORDER_STATUS_CHANGED,
    PRODUCT_CREATED,
    PRODUCT_UPDATED,
    PRODUCT_DELETED,
    VARIANT_CREATED,
    VARIANT_UPDATED,
    STOCK_ADJUSTED,
    COUPON_CREATED,
    COUPON_DEACTIVATED,
    FLASH_SALE_CREATED,
    WARRANTY_RESOLVED,
    USER_CREATED,
    USER_LOCKED,
    USER_UNLOCKED,
    USER_ROLE_CHANGED,
    LOGIN_LOCKOUT_CLEARED,
    ORDERS_EXPORTED
}
