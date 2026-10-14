package dev.techmart.common.error;

/**
 * Goc của mọi ngoại lệ nghiệp vụ. GlobalExceptionHandler bat loai này và dùng envelope lỗi
 * theo docs/03-api-contract.md mục 1.
 *
 * <p>Không ke thua từ Exception có checked: logic nghiệp vụ thất bại là chuyen binh thuong,
 * không nên buoc mọi tầng gọi phải khai báo throws.
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

    // --- Các lop con đặt ten chờ để đọc o chỗ gọi ---

    public static BusinessException notFound(ErrorCode code, Object id) {
        return new BusinessException(code, code.defaultMessage() + ": " + id);
    }

    /** Lỗi trả về kem chi tiết tung variant thieu bao nhiêu - xem docs/04 mục 4. */
    public static BusinessException insufficientStock(Object details) {
        return new BusinessException(ErrorCode.INSUFFICIENT_STOCK,
                ErrorCode.INSUFFICIENT_STOCK.defaultMessage(), details);
    }
}
