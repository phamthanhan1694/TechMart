package dev.techmart.warranty.dto;

import dev.techmart.warranty.domain.ProductSerial;
import dev.techmart.warranty.domain.SerialStatus;
import dev.techmart.warranty.domain.WarrantyRequest;
import dev.techmart.warranty.domain.WarrantyStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;

public final class WarrantyDtos {

    private WarrantyDtos() {
    }

    // ------------------------------------------------------------------ serial

    public record BulkSerialRequest(
            @NotNull Long variantId,
            @NotEmpty @Size(max = 1000) List<@NotBlank @Size(max = 64) String> serialNumbers,
            @Min(1) @Max(120) Integer warrantyMonths
    ) {}

    public record BulkSerialResponse(
            int imported,
            int skippedDuplicates,
            @Schema(description = "Serial bi bo qua vi da ton tai") List<String> duplicates
    ) {}

    @Schema(description = "Ket qua tra cuu bao hanh - endpoint cong khai, khong can dang nhap")
    public record WarrantyLookupResponse(
            String serialNo,
            String productName,
            String variantName,
            SerialStatus status,
            @Schema(description = "null neu hang chua xuat kho") Instant activatedAt,
            Instant expiresAt,
            boolean underWarranty,
            int warrantyMonths,
            String message
    ) {
        public static WarrantyLookupResponse from(ProductSerial s, Instant now) {
            boolean active = s.isUnderWarranty(now);
            String message;
            if (s.getActivatedAt() == null) {
                message = "Serial hop le nhung hang chua duoc ban ra";
            } else if (active) {
                message = "Con bao hanh";
            } else {
                message = "Da het han bao hanh";
            }
            return new WarrantyLookupResponse(
                    s.getSerialNo(), s.getVariant().getProduct().getName(), s.getVariant().getName(),
                    s.getStatus(), s.getActivatedAt(), s.warrantyExpiresAt(), active,
                    s.getWarrantyMonths(), message);
        }
    }

    public record SerialResponse(
            Long id, String serialNo, SerialStatus status, Instant activatedAt) {
        public static SerialResponse from(ProductSerial s) {
            return new SerialResponse(s.getId(), s.getSerialNo(), s.getStatus(), s.getActivatedAt());
        }
    }

    // ------------------------------------------------------------------ bảo hành

    public record CreateWarrantyRequest(
            @NotBlank @Size(max = 64) String serialNo,
            @NotBlank @Size(max = 1000) String reason
    ) {}

    public record ResolveWarrantyRequest(
            @NotNull WarrantyStatus toStatus,
            @Size(max = 1000) String resolution
    ) {}

    public record WarrantyRequestResponse(
            Long id,
            String serialNo,
            String productName,
            WarrantyStatus status,
            String reason,
            String resolution,
            Instant createdAt,
            Instant resolvedAt
    ) {
        public static WarrantyRequestResponse from(WarrantyRequest w) {
            return new WarrantyRequestResponse(
                    w.getId(), w.getSerial().getSerialNo(),
                    w.getSerial().getVariant().getProduct().getName(),
                    w.getStatus(), w.getReason(), w.getResolution(),
                    w.getCreatedAt(), w.getResolvedAt());
        }
    }
}
