package dev.techmart.common.error;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Envelope loi duy nhat cua toan bo API (docs/03-api-contract.md muc 1).
 *
 * <p>Client hien thi theo {@code code}, khong parse {@code message}.
 * {@code traceId} doi chieu duoc voi log khi dieu tra su co.
 */
@Schema(description = "Cau truc loi chung cua toan bo API")
public record ApiError(
        @Schema(example = "2026-09-01T10:23:45Z") Instant timestamp,
        @Schema(example = "409") int status,
        @Schema(example = "INSUFFICIENT_STOCK") String code,
        @Schema(example = "San pham khong du so luong") String message,
        @Schema(example = "/api/v1/orders") String path,
        @Schema(example = "a1b2c3d4") String traceId,
        @Schema(description = "Chi co o loi validation va loi ton kho") Object details
) {
    public static ApiError of(ErrorCode code, String message, String path, String traceId, Object details) {
        return new ApiError(Instant.now(), code.status().value(), code.name(), message, path, traceId, details);
    }
}
