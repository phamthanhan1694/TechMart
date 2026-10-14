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
@Tag(name = "Auth", description = "Dang ky, dang nhap, xoay vong token")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @SecurityRequirements
    @Operation(summary = "Dang ky tai khoan khach hang",
            description = "Luon tao voi role CUSTOMER. Khong nhan role tu client.")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(req));
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Dang nhap",
            description = "Sai email va sai mat khau tra cung ma INVALID_CREDENTIALS "
                    + "de khong tiet lo email nao ton tai.")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(summary = "Doi cap token moi",
            description = "Refresh token duoc xoay vong: token cu bi thu hoi ngay. "
                    + "Dung lai token da thu hoi se thu hoi toan bo chuoi token cua user.")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest req) {
        return authService.refresh(req);
    }

    @PostMapping("/logout")
    @Operation(summary = "Dang xuat", description = "Thu hoi refresh token o phia server.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest req) {
        authService.logout(req);
    }

    @PostMapping("/logout-all")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Dang xuat khoi moi thiet bi")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logoutAll(@AuthenticationPrincipal AuthPrincipal principal) {
        authService.logoutAllDevices(principal.id());
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Thong tin nguoi dang dang nhap")
    public UserResponse me(@AuthenticationPrincipal AuthPrincipal principal) {
        return authService.me(principal.id());
    }
}
