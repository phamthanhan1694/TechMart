package dev.techmart.identity.web;

import dev.techmart.identity.dto.AuthDtos.*;
import dev.techmart.identity.security.AuthPrincipal;
import dev.techmart.identity.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Đăng ký, đăng nhập, xoay vòng token")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @SecurityRequirements
    @Operation(summary = "Đăng ký tài khoản khách hàng",
            description = "Luôn tạo với role CUSTOMER. Không nhận role từ client.")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(req));
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Đăng nhập",
            description = "Sai email và sai mật khẩu trả cùng mã INVALID_CREDENTIALS "
                    + "để không tiết lộ email nào tồn tại.")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(summary = "Đổi cặp token mới",
            description = "Refresh token được xoay vòng: token cũ bị thu hồi ngay. "
                    + "Dùng lại token đã thu hồi sẽ thu hồi toàn bộ chuỗi token của user.")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest req) {
        return authService.refresh(req);
    }

    @PostMapping("/logout")
    @Operation(summary = "Đăng xuất", description = "Thu hồi refresh token ở phía server.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest req) {
        authService.logout(req);
    }

    @PostMapping("/logout-all")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Đăng xuất khỏi mọi thiết bị")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logoutAll(@AuthenticationPrincipal AuthPrincipal principal) {
        authService.logoutAllDevices(principal.id());
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Thông tin người đang đăng nhập")
    public UserResponse me(@AuthenticationPrincipal AuthPrincipal principal) {
        return authService.me(principal.id());
    }
}
