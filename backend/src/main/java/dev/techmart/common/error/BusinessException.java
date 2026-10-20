package dev.techmart.common.error;

/**
 * Gốc của mọi ngoại lệ nghiệp vụ. GlobalExceptionHandler bắt loại này và dùng envelope lỗi
 * theo docs/03-api-contract.md mục 1.
 *
 * <p>Không kế thừa từ Exception có checked: logic nghiệp vụ thất bại là chuyện bình thường,
 * không nên buộc mọi tầng gọi phải khai báo throws.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode code;
    private final transient Object details;

    public BusinessException(ErrorCode code) {
        this(code, code.defaultMessage(), null);
    }

    public BusinessException(ErrorCode code, String message) {
        this(code, message, null);
    }

    public BusinessException(ErrorCode code, String message, Object details) {
        super(message);
        this.code = code;
        this.details = details;
    }

    public ErrorCode code() {
        return code;
    }

    public Object details() {
        return details;
    }

    // --- Các lớp con đặt tên cho dễ đọc ở chỗ gọi ---

    public static BusinessException notFound(ErrorCode code, Object id) {
        return new BusinessException(code, code.defaultMessage() + ": " + id);
    }

    /** Lỗi trả về kèm chi tiết từng variant thiếu bao nhiêu - xem docs/04 mục 4. */
    public static BusinessException insufficientStock(Object details) {
        return new BusinessException(ErrorCode.INSUFFICIENT_STOCK,
                ErrorCode.INSUFFICIENT_STOCK.defaultMessage(), details);
    }
}
