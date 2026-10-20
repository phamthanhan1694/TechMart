package dev.techmart.identity.web;

import dev.techmart.common.web.PageResponse;
import dev.techmart.common.web.PageableSanitizer;
import dev.techmart.identity.domain.Role;
import dev.techmart.identity.domain.UserStatus;
import dev.techmart.identity.dto.AccountDtos.*;
import dev.techmart.identity.security.AuthPrincipal;
import dev.techmart.identity.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Admin - Người dùng", description = "Tra cứu khách hàng, khoá/mở, đổi vai trò, tạo tài khoản nhân viên")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService service;

    public AdminUserController(AdminUserService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Danh sách người dùng",
            description = "Tìm theo email/tên/số điện thoại. sort nhận: createdAt, totalSpent, paidOrders, lastOrderAt, email.")
    public PageResponse<AdminUserRow> search(@RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) Role role,
                                             @RequestParam(required = false) UserStatus status,
                                             @PageableDefault(size = 20) Pageable pageable) {
        return service.search(keyword, role, status, PageableSanitizer.sanitize(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết người dùng", description = "Kèm 10 đơn gần nhất và trạng thái khoá đăng nhập tạm thời.")
    public AdminUserDetail detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @PostMapping
    @Operation(summary = "Tạo tài khoản (thường là STAFF)")
    public ResponseEntity<AdminUserRow> create(@Valid @RequestBody CreateUserRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Khoá / mở khoá tài khoản",
            description = "Khoá thì thu hồi mọi refresh token. Không tự khoá mình; không khoá ADMIN cuối cùng.")
    public AdminUserRow changeStatus(@AuthenticationPrincipal AuthPrincipal principal,
                                     @PathVariable Long id, @Valid @RequestBody ChangeStatusRequest req) {
        return service.changeStatus(principal, id, req);
    }

    @PatchMapping("/{id}/role")
    @Operation(summary = "Đổi vai trò", description = "Không tự đổi vai trò của mình; không hạ ADMIN cuối cùng.")
    public AdminUserRow changeRole(@AuthenticationPrincipal AuthPrincipal principal,
                                   @PathVariable Long id, @Valid @RequestBody ChangeRoleRequest req) {
        return service.changeRole(principal, id, req);
    }

    @DeleteMapping("/{id}/login-lockout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Gỡ khoá đăng nhập tạm thời", description = "Cho khách gọi lên tổng đài sau khi gõ sai mật khẩu nhiều lần.")
    public void clearLoginLockout(@PathVariable Long id) {
        service.clearLoginLockout(id);
    }
}
