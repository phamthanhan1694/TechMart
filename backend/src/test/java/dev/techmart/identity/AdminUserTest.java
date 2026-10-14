package dev.techmart.identity;

import dev.techmart.audit.domain.AuditAction;
import dev.techmart.audit.domain.AuditLog;
import dev.techmart.audit.repository.AuditLogRepository;
import dev.techmart.catalog.domain.ProductVariant;
import dev.techmart.common.error.BusinessException;
import dev.techmart.common.error.ErrorCode;
import dev.techmart.identity.domain.Role;
import dev.techmart.identity.domain.User;
import dev.techmart.identity.domain.UserStatus;
import dev.techmart.identity.dto.AccountDtos.*;
import dev.techmart.identity.dto.AuthDtos.LoginRequest;
import dev.techmart.identity.dto.AuthDtos.RefreshRequest;
import dev.techmart.identity.dto.AuthDtos.TokenResponse;
import dev.techmart.identity.security.AuthPrincipal;
import dev.techmart.identity.service.AdminUserService;
import dev.techmart.identity.service.AuthService;
import dev.techmart.order.domain.OrderStatus;
import dev.techmart.support.DatabaseCleaner;
import dev.techmart.support.IntegrationTestBase;
import dev.techmart.support.TestDataFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Quản lý người dùng cho ADMIN - và audit log đi kèm từng thao tác. */
@DisplayName("Quan ly nguoi dung (admin)")
class AdminUserTest extends IntegrationTestBase {

    @Autowired AdminUserService adminUsers;
    @Autowired AuthService authService;
    @Autowired AuditLogRepository auditLogs;
    @Autowired TestDataFactory fixtures;
    @Autowired DatabaseCleaner cleaner;

    private User admin;
    private AuthPrincipal adminPrincipal;

    @BeforeEach
    void setUp() {
        cleaner.clean();
        admin = fixtures.createUser(Role.ADMIN);
        adminPrincipal = new AuthPrincipal(admin.getId(), admin.getEmail(), Role.ADMIN);
        actAs(adminPrincipal);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Khoa tai khoan -> thu hoi refresh token, dang nhap bi chan, audit log ghi ai khoa")
    void lock_revokesTokens_blocksLogin_andIsAudited() {
        User customer = fixtures.createCustomer();
        TokenResponse tokens = authService.login(new LoginRequest(customer.getEmail(), "Password@123"));

        AdminUserRow row = adminUsers.changeStatus(adminPrincipal, customer.getId(),
                new ChangeStatusRequest(UserStatus.LOCKED, "Nghi gian lan"));

        assertThat(row.status()).isEqualTo(UserStatus.LOCKED);
        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(tokens.refreshToken())))
                .as("Phien cu phai chet ngay, khong doi refresh token het han 7 ngay")
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> authService.login(new LoginRequest(customer.getEmail(), "Password@123")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.ACCOUNT_LOCKED);

        List<AuditLog> logs = auditLogs.findAll();
        assertThat(logs).singleElement().satisfies(log -> {
            assertThat(log.getAction()).isEqualTo(AuditAction.USER_LOCKED);
            assertThat(log.getActorEmail()).isEqualTo(admin.getEmail());
            assertThat(log.getTargetId()).isEqualTo(String.valueOf(customer.getId()));
            assertThat(log.getDetail()).containsEntry("reason", "Nghi gian lan");
        });
    }

    @Test
    @DisplayName("Khong the tu khoa minh hay tu doi vai tro cua minh")
    void cannotModifySelf() {
        assertThatThrownBy(() -> adminUsers.changeStatus(adminPrincipal, admin.getId(),
                new ChangeStatusRequest(UserStatus.LOCKED, null)))
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.CANNOT_MODIFY_SELF);
        assertThatThrownBy(() -> adminUsers.changeRole(adminPrincipal, admin.getId(),
                new ChangeRoleRequest(Role.STAFF)))
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.CANNOT_MODIFY_SELF);
    }

    @Test
    @DisplayName("Khong duoc de he thong khong con ADMIN nao, ke ca qua access token con song cua admin da bi khoa")
    void cannotRemoveLastActiveAdmin() {
        User other = fixtures.createUser(Role.ADMIN);
        AuthPrincipal otherPrincipal = new AuthPrincipal(other.getId(), other.getEmail(), Role.ADMIN);

        // other khoá admin -> chỉ còn other là ADMIN đang hoạt động
        actAs(otherPrincipal);
        adminUsers.changeStatus(otherPrincipal, admin.getId(), new ChangeStatusRequest(UserStatus.LOCKED, null));

        // admin da bi khoá nhưng access token (tối đa 15 phút) vẫn mang role ADMIN
        actAs(adminPrincipal);
        assertThatThrownBy(() -> adminUsers.changeRole(adminPrincipal, other.getId(), new ChangeRoleRequest(Role.STAFF)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.LAST_ADMIN);
    }

    @Test
    @DisplayName("Doi vai tro thu hoi token de lan lam moi sau phat token mang vai tro moi")
    void changeRole_revokesTokens() {
        User staff = fixtures.createUser(Role.STAFF);
        TokenResponse tokens = authService.login(new LoginRequest(staff.getEmail(), "Password@123"));

        AdminUserRow row = adminUsers.changeRole(adminPrincipal, staff.getId(), new ChangeRoleRequest(Role.ADMIN));

        assertThat(row.role()).isEqualTo(Role.ADMIN);
        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(tokens.refreshToken())))
                .isInstanceOf(BusinessException.class);
        assertThat(auditLogs.countByAction(AuditAction.USER_ROLE_CHANGED)).isEqualTo(1);
    }

    @Test
    @DisplayName("Danh sach kem tong chi tieu; tu khoa '%' khong khop tat ca")
    void search_withSpendingStats_andEscapedKeyword() {
        User buyer = fixtures.createCustomer();
        ProductVariant variant = fixtures.createVariantWithStock(10, "1000000.00");
        fixtures.insertOrder(buyer.getId(), variant.getId(), OrderStatus.DELIVERED, Instant.now(), "2500000", "Ha Noi", null, 1);
        fixtures.insertOrder(buyer.getId(), variant.getId(), OrderStatus.CANCELLED, Instant.now(), "9000000", "Ha Noi", null, 1);

        var page = adminUsers.search(buyer.getEmail().substring(0, 6), null, null,
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "totalSpent")));
        AdminUserRow row = page.content().stream().filter(r -> r.id().equals(buyer.getId())).findFirst().orElseThrow();
        assertThat(row.paidOrders()).as("Don huy khong tinh").isEqualTo(1);
        assertThat(row.totalSpent()).isEqualByComparingTo("2500000");

        assertThat(adminUsers.search("%", null, null, PageRequest.of(0, 20)).totalElements())
                .as("'%' la ky tu thuong, khong phai ky tu dai dien")
                .isZero();
        assertThat(adminUsers.search(null, Role.ADMIN, null, PageRequest.of(0, 20)).totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("Tao tai khoan nhan vien -> dang nhap duoc voi vai tro STAFF")
    void createStaff() {
        AdminUserRow row = adminUsers.create(new CreateUserRequest("kho2@test.dev", "Staff@1234",
                "Nhan vien kho 2", null, Role.STAFF));

        assertThat(row.role()).isEqualTo(Role.STAFF);
        assertThat(authService.login(new LoginRequest("kho2@test.dev", "Staff@1234")).accessToken()).isNotBlank();
        assertThat(auditLogs.countByAction(AuditAction.USER_CREATED)).isEqualTo(1);
    }

    private static void actAs(AuthPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }
}
