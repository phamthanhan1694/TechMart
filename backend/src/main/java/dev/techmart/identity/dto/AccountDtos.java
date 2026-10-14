package dev.techmart.identity.dto;

import dev.techmart.identity.domain.Role;
import dev.techmart.identity.domain.UserStatus;
import dev.techmart.order.dto.OrderDtos.OrderSummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** DTO cho tài khoản cá nhân (khách tự sửa) và quản lý người dùng (admin). */
public final class AccountDtos {

    private AccountDtos() {
    }

    // ------------------------------------------------------------------ tài khoản cá nhân

    public record UpdateProfileRequest(
            @NotBlank @Size(max = 150) @Schema(example = "Nguyen Van A") String fullName,
            @Pattern(regexp = "^$|^0\\d{9}$", message = "So dien thoai phai co 10 chu so va bat dau bang 0")
            @Schema(example = "0901234567") String phone
    ) {}

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 72) @Schema(description = "Toi thieu 8 ky tu") String newPassword
    ) {}

    // ------------------------------------------------------------------ quản lý người dùng

    @Schema(description = "Mot dong trong danh sach nguoi dung, kem so lieu mua hang")
    public record AdminUserRow(
            Long id,
            String email,
            String fullName,
            String phone,
            Role role,
            UserStatus status,
            Instant createdAt,
            @Schema(description = "So don da thu tien") long paidOrders,
            BigDecimal totalSpent,
            Instant lastOrderAt
    ) {}

    public record AdminUserDetail(
            AdminUserRow user,
            @Schema(description = "So giay con bi khoa dang nhap tam thoi; 0 = khong bi khoa")
            long loginLockedSeconds,
            List<OrderSummaryResponse> recentOrders
    ) {}

    public record CreateUserRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 150) String fullName,
            @Pattern(regexp = "^$|^0\\d{9}$", message = "So dien thoai phai co 10 chu so va bat dau bang 0")
            String phone,
            @NotNull Role role
    ) {}

    public record ChangeStatusRequest(
            @NotNull UserStatus status,
            @Size(max = 500) String reason
    ) {}

    public record ChangeRoleRequest(@NotNull Role role) {}
}
