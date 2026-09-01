package dev.techmart.warranty.domain;

public enum SerialStatus {
    /** Con trong kho, chua gan cho don nao. */
    IN_STOCK,
    /** Da xuat kho va gan cho mot dong don hang. */
    SOLD,
    /** Khach tra lai, dang cho kiem tra. */
    RETURNED,
    /** Loi, khong ban lai duoc. */
    DEFECTIVE
}
