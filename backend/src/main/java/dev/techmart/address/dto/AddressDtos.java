package dev.techmart.address.dto;

import dev.techmart.address.domain.UserAddress;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class AddressDtos {

    private AddressDtos() {
    }

    /** Ràng buộc giống hệt {@code OrderDtos.ShippingAddress}: địa chỉ lưu được thì đặt hàng được. */
    public record AddressRequest(
            @Size(max = 50) @Schema(example = "Nhà riêng") String label,
            @NotBlank @Size(max = 150) String receiverName,
            @NotBlank @Pattern(regexp = "^0\\d{9}$", message = "Số điện thoại phải có 10 chữ số") String phone,
            @NotBlank @Size(max = 255) String street,
            @NotBlank @Size(max = 100) String ward,
            @NotBlank @Size(max = 100) String district,
            @NotBlank @Size(max = 100) String province,
            @Schema(description = "Đặt làm địa chỉ mặc định") boolean makeDefault
    ) {}

    public record AddressResponse(
            Long id,
            String label,
            String receiverName,
            String phone,
            String street,
            String ward,
            String district,
            String province,
            boolean isDefault,
            Instant createdAt
    ) {
        public static AddressResponse from(UserAddress a) {
            return new AddressResponse(a.getId(), a.getLabel(), a.getReceiverName(), a.getPhone(),
                    a.getStreet(), a.getWard(), a.getDistrict(), a.getProvince(), a.isDefault(), a.getCreatedAt());
        }
    }
}
