package dev.techmart.common.error;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Envelope lỗi duy nhất của toàn bộ API (docs/03-api-contract.md mục 1).
 *
 * <p>Client hiển thị theo {@code code}, không parse {@code message}.
 * {@code traceId} đổi chieu được với log khi dieu trả su có.
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
