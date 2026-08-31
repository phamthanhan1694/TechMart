package dev.techmart.inventory.domain;

public enum ReservationStatus {
    /** Dang giu hang, chua het han. */
    HELD,
    /** Da chuyen thanh don hang. */
    COMMITTED,
    /** Khach chu dong huy phien checkout. */
    RELEASED,
    /** Het han ma khong thanh toan - job quet nha ra. */
    EXPIRED
}
