package dev.techmart.common.error;

/**
 * Goc cua moi ngoai le nghiep vu. GlobalExceptionHandler bat loai nay va dung envelope loi
 * theo docs/03-api-contract.md muc 1.
 *
 * <p>Khong ke thua tu Exception co checked: logic nghiep vu that bai la chuyen binh thuong,
 * khong nen buoc moi tang goi phai khai bao throws.
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

    // --- Cac lop con dat ten cho de doc o cho goi ---

    public static BusinessException notFound(ErrorCode code, Object id) {
        return new BusinessException(code, code.defaultMessage() + ": " + id);
    }

    /** Loi tra ve kem chi tiet tung variant thieu bao nhieu - xem docs/04 muc 4. */
    public static BusinessException insufficientStock(Object details) {
        return new BusinessException(ErrorCode.INSUFFICIENT_STOCK,
                ErrorCode.INSUFFICIENT_STOCK.defaultMessage(), details);
    }
}
