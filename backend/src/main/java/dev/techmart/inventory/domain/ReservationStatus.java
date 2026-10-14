package dev.techmart.inventory.domain;

public enum ReservationStatus {
    /** Dạng giữ hàng, chưa hết hạn. */
    HELD,
    /** Da chuyen thanh đơn hàng. */
    COMMITTED,
    /** Khách chủ động huỷ phiên checkout. */
    RELEASED,
    /** Hết hạn mà không thanh toán - job quét nhả ra. */
    EXPIRED
}
