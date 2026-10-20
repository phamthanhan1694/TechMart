package dev.techmart.inventory.domain;

public enum ReservationStatus {
    /** Đang giữ hàng, chưa hết hạn. */
    HELD,
    /** Đã chuyển thành đơn hàng. */
    COMMITTED,
    /** Khách chủ động huỷ phiên checkout. */
    RELEASED,
    /** Hết hạn mà không thanh toán - job quét nhả ra. */
    EXPIRED
}
