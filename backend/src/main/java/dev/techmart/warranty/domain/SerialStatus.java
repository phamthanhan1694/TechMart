package dev.techmart.warranty.domain;

public enum SerialStatus {
    /** Con trong kho, chưa gan chờ đơn nào. */
    IN_STOCK,
    /** Đã xuất kho và gan cho một dòng đơn hàng. */
    SOLD,
    /** Khách trả lại, dạng chờ kiểm tra. */
    RETURNED,
    /** Lỗi, không bán lại được. */
    DEFECTIVE
}
