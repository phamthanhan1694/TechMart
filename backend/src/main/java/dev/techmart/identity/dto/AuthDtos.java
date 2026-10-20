package dev.techmart.identity.dto;

import dev.techmart.identity.domain.Role;
import dev.techmart.identity.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** DTO của module identity. Gom vào một file vì đều nhỏ và luôn đọc cùng nhau. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 255)
            @Schema(example = "nguyenvana@example.com") String email,

            @NotBlank @Size(min = 8, max = 72)
            @Schema(example = "MatKhau@123", description = "Tối thiểu 8 ký tự") String password,

            @NotBlank @Size(max = 150)
            @Schema(example = "Nguyễn Văn A") String fullName,

            @Pattern(regexp = "^$|^0\\d{9}$", message = "Số điện thoại phải có 10 chữ số và bắt dau bằng 0")
            @Schema(example = "0901234567") String phone
    ) {}

    public record LoginRequest(
            @NotBlank @Email @Schema(example = "customer@techmart.dev") String email,
            @NotBlank @Schema(example = "Customer@123") String password
    ) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    @Schema(description = "Cặp token. refreshToken chỉ hiển thị một lần duy nhất.")
    public record TokenResponse(
            String accessToken,
            String refreshToken,
            @Schema(example = "Bearer") String tokenType,
            @Schema(example = "900", description = "Số giây còn lại của accessToken") long expiresIn
    ) {
        public static TokenResponse of(String access, String refresh, long ttlSeconds) {
            return new TokenResponse(access, refresh, "Bearer", ttlSeconds);
        }
    }

    public record UserResponse(
            Long id,
            String email,
            String fullName,
            String phone,
            Role role,
            Instant createdAt
    ) {
        /** Không bao giờ map passwordHash sang DTO. */
        public static UserResponse from(User u) {
            return new UserResponse(u.getId(), u.getEmail(), u.getFullName(), u.getPhone(), u.getRole(), u.getCreatedAt());
        }
    }
}
