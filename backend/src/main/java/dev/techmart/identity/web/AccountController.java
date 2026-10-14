package dev.techmart.identity.web;

import dev.techmart.identity.dto.AccountDtos.ChangePasswordRequest;
import dev.techmart.identity.dto.AccountDtos.UpdateProfileRequest;
import dev.techmart.identity.dto.AuthDtos.UserResponse;
import dev.techmart.identity.security.AuthPrincipal;
import dev.techmart.identity.service.AccountService;
import dev.techmart.identity.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/account")
@Tag(name = "Account", description = "Ho so ca nhan va mat khau")
public class AccountController {

    private final AccountService accountService;
    private final AuthService authService;

    public AccountController(AccountService accountService, AuthService authService) {
        this.accountService = accountService;
        this.authService = authService;
    }

    @GetMapping("/profile")
    @Operation(summary = "Ho so cua toi")
    public UserResponse profile(@AuthenticationPrincipal AuthPrincipal principal) {
        return authService.me(principal.id());
    }

    @PutMapping("/profile")
    @Operation(summary = "Cap nhat ho so", description = "Chi doi duoc ho ten va so dien thoai. Email la dinh danh dang nhap.")
    public UserResponse updateProfile(@AuthenticationPrincipal AuthPrincipal principal,
                                      @Valid @RequestBody UpdateProfileRequest req) {
        return accountService.updateProfile(principal.id(), req);
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Doi mat khau",
            description = """
                    Sai mat khau hien tai tra `422 CURRENT_PASSWORD_INCORRECT` (khong phai 401 -
                    client coi 401 la het phien). Thanh cong thi thu hoi MOI refresh token:
                    moi thiet bi phai dang nhap lai.
                    """)
    public void changePassword(@AuthenticationPrincipal AuthPrincipal principal,
                               @Valid @RequestBody ChangePasswordRequest req) {
        accountService.changePassword(principal.id(), req);
    }
}
