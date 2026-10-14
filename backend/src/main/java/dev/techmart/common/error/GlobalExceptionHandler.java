package dev.techmart.common.error;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.UUID;

/**
 * Biến mọi ngoại lệ thành chung một envelope lỗi. Không rải try-catch trong controller.
 *
 * <p>Nguyên tắc: không bao giờ trả stack trace ra response. Lỗi 500 chỉ trả code + traceId,
 * phần còn lại nằm trong log (docs/07-security-checklist.md mục 4).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException ex, HttpServletRequest req) {
        String traceId = traceId();
        // Lỗi nghiệp vụ là chuyen binh thuong -> WARN, không kem stack trace
        log.warn("[{}] {} tai {}: {}", traceId, ex.code(), req.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(ex.code().status())
                .body(ApiError.of(ex.code(), ex.getMessage(), req.getRequestURI(), traceId, ex.details()));
    }

    /** Bean Validation thất bại -> liet ke tung field sai trong details. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::toViolation)
                .toList();
        return build(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage(), req, violations);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> handleMalformed(Exception ex, HttpServletRequest req) {
        return build(ErrorCode.MALFORMED_REQUEST, ErrorCode.MALFORMED_REQUEST.defaultMessage(), req, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuth(AuthenticationException ex, HttpServletRequest req) {
        return build(ErrorCode.UNAUTHENTICATED, ErrorCode.UNAUTHENTICATED.defaultMessage(), req, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return build(ErrorCode.ACCESS_DENIED, ErrorCode.ACCESS_DENIED.defaultMessage(), req, null);
    }

    /**
     * Hai transaction cùng sửa một bản ghi. Trả 409 để client thử lại - đây là hành vi đúng
     * ve nghiệp vụ, không phải lỗi hệ thống (docs/02-luồng-nghiep-vu.md mục 1).
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest req) {
        return build(ErrorCode.CONCURRENT_MODIFICATION, ErrorCode.CONCURRENT_MODIFICATION.defaultMessage(), req, null);
    }

    /**
     * Vi phạm ràng buộc DB. Đây thường là chốt chặn cuối cùng đã làm việc của nó
     * (vd CHECK stock_quantity >= 0) - log ERROR vì no báo hieu logic khoá o trên đã hở.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest req) {
        String traceId = traceId();
        log.error("[{}] Vi pham rang buoc DB tai {}", traceId, req.getRequestURI(), ex);
        ErrorCode code = ErrorCode.CONCURRENT_MODIFICATION;
        return ResponseEntity.status(code.status())
                .body(ApiError.of(code, "Du lieu xung dot, vui long thu lai", req.getRequestURI(), traceId, null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest req) {
        String traceId = traceId();
        log.error("[{}] Loi khong luong truoc tai {}", traceId, req.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage(),
                        req.getRequestURI(), traceId, null));
    }

    private ResponseEntity<ApiError> build(ErrorCode code, String msg, HttpServletRequest req, Object details) {
        String traceId = traceId();
        log.warn("[{}] {} tai {}", traceId, code, req.getRequestURI());
        return ResponseEntity.status(code.status())
                .body(ApiError.of(code, msg, req.getRequestURI(), traceId, details));
    }

    private static String traceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static FieldViolation toViolation(FieldError e) {
        return new FieldViolation(e.getField(), e.getDefaultMessage());
    }

    public record FieldViolation(String field, String message) {}
}
