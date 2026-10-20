package dev.techmart.warranty.domain;

public enum SerialStatus {
    /** Còn trong kho, chưa gán cho đơn nào. */
    IN_STOCK,
    /** Đã xuất kho và gán cho một dòng đơn hàng. */
    SOLD,
    /** Khách trả lại, đang chờ kiểm tra. */
    RETURNED,
    /** Lỗi, không bán lại được. */
    DEFECTIVE
}
