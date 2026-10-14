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
@Tag(name = "Admin - Nguoi dung", description = "Tra cuu khach hang, khoa/mo, doi vai tro, tao tai khoan nhan vien")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService service;

    public AdminUserController(AdminUserService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Danh sach nguoi dung",
            description = "Tim theo email/ten/so dien thoai. sort nhan: createdAt, totalSpent, paidOrders, lastOrderAt, email.")
    public PageResponse<AdminUserRow> search(@RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) Role role,
                                             @RequestParam(required = false) UserStatus status,
                                             @PageableDefault(size = 20) Pageable pageable) {
        return service.search(keyword, role, status, PageableSanitizer.sanitize(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiet nguoi dung", description = "Kem 10 don gan nhat va trang thai khoa dang nhap tam thoi.")
    public AdminUserDetail detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @PostMapping
    @Operation(summary = "Tao tai khoan (thuong la STAFF)")
    public ResponseEntity<AdminUserRow> create(@Valid @RequestBody CreateUserRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Khoa / mo khoa tai khoan",
            description = "Khoa thi thu hoi moi refresh token. Khong tu khoa minh; khong khoa ADMIN cuoi cung.")
    public AdminUserRow changeStatus(@AuthenticationPrincipal AuthPrincipal principal,
                                     @PathVariable Long id, @Valid @RequestBody ChangeStatusRequest req) {
        return service.changeStatus(principal, id, req);
    }

    @PatchMapping("/{id}/role")
    @Operation(summary = "Doi vai tro", description = "Khong tu doi vai tro cua minh; khong ha ADMIN cuoi cung.")
    public AdminUserRow changeRole(@AuthenticationPrincipal AuthPrincipal principal,
                                   @PathVariable Long id, @Valid @RequestBody ChangeRoleRequest req) {
        return service.changeRole(principal, id, req);
    }

    @DeleteMapping("/{id}/login-lockout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Go khoa dang nhap tam thoi", description = "Cho khach goi len tong dai sau khi go sai mat khau nhieu lan.")
    public void clearLoginLockout(@PathVariable Long id) {
        service.clearLoginLockout(id);
    }
}
