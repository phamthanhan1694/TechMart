package dev.techmart.identity.web;

import dev.techmart.identity.dto.AccountDtos.ChangePasswordRequest;
import dev.techmart.identity.dto.AccountDtos.UpdateProfileRequest;
import dev.techmart.identity.dto.AuthDtos.UserResponse;
import dev.techmart.identity.security.AuthPrincipal;
import dev.techmart.identity.service.AccountService;
import dev.techmart.identity.service.AuthService;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/account")
@Tag(name = "Account", description = "Hồ sơ cá nhân và mật khẩu")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final AuthService authService;

    @GetMapping("/profile")
    @Operation(summary = "Hồ sơ của tôi")
    public UserResponse profile(@AuthenticationPrincipal AuthPrincipal principal) {
        return authService.me(principal.id());
    }

    @PutMapping("/profile")
    @Operation(summary = "Cập nhật hồ sơ", description = "Chỉ đổi được họ tên và số điện thoại. Email là định danh đăng nhập.")
    public UserResponse updateProfile(@AuthenticationPrincipal AuthPrincipal principal,
                                      @Valid @RequestBody UpdateProfileRequest req) {
        return accountService.updateProfile(principal.id(), req);
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Đổi mật khẩu",
            description = """
                    Sai mật khẩu hiện tại trả `422 CURRENT_PASSWORD_INCORRECT` (không phải 401 -
                    client coi 401 là hết phiên). Thành công thì thu hồi MỌI refresh token:
                    mọi thiết bị phải đăng nhập lại.
                    """)
    public void changePassword(@AuthenticationPrincipal AuthPrincipal principal,
                               @Valid @RequestBody ChangePasswordRequest req) {
        accountService.changePassword(principal.id(), req);
    }
}
