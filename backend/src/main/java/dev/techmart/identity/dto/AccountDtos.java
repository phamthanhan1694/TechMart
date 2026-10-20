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
            @NotBlank @Size(max = 150) @Schema(example = "Nguyễn Văn A") String fullName,
            @Pattern(regexp = "^$|^0\\d{9}$", message = "Số điện thoại phải có 10 chữ số và bắt dau bằng 0")
            @Schema(example = "0901234567") String phone
    ) {}

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 72) @Schema(description = "Tối thiểu 8 ký tự") String newPassword
    ) {}

    // ------------------------------------------------------------------ quản lý người dùng

    @Schema(description = "Một dòng trong danh sách người dùng, kèm số liệu mua hàng")
    public record AdminUserRow(
            Long id,
            String email,
            String fullName,
            String phone,
            Role role,
            UserStatus status,
            Instant createdAt,
            @Schema(description = "Số đơn đã thu tiền") long paidOrders,
            BigDecimal totalSpent,
            Instant lastOrderAt
    ) {}

    public record AdminUserDetail(
            AdminUserRow user,
            @Schema(description = "Số giây còn bị khoá đăng nhập tạm thời; 0 = không bị khoá")
            long loginLockedSeconds,
            List<OrderSummaryResponse> recentOrders
    ) {}

    public record CreateUserRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 150) String fullName,
            @Pattern(regexp = "^$|^0\\d{9}$", message = "Số điện thoại phải có 10 chữ số và bắt dau bằng 0")
            String phone,
            @NotNull Role role
    ) {}

    public record ChangeStatusRequest(
            @NotNull UserStatus status,
            @Size(max = 500) String reason
    ) {}

    public record ChangeRoleRequest(@NotNull Role role) {}
}
