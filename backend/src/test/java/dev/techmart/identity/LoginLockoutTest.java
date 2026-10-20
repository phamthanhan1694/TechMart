package dev.techmart.identity;

import dev.techmart.common.error.BusinessException;
import dev.techmart.common.error.ErrorCode;
import dev.techmart.identity.dto.AuthDtos.LoginRequest;
import dev.techmart.identity.dto.AuthDtos.RegisterRequest;
import dev.techmart.identity.dto.AuthDtos.UserResponse;
import dev.techmart.identity.service.AdminUserService;
import dev.techmart.identity.service.AuthService;
import dev.techmart.support.DatabaseCleaner;
import dev.techmart.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Khoá đăng nhập tạm thời theo tài khoản - ADR-0012.
 *
 * <p>Ngưỡng lấy từ application.yml: sai 5 lần trong 15 phút thì khoá 15 phút.
 */
@DisplayName("Khoá đăng nhập theo tài khoản")
class LoginLockoutTest extends IntegrationTestBase {

    private static final String EMAIL = "khoa@test.dev";
    private static final String PASSWORD = "MatKhau@123";
    private static final String WRONG = "SaiRoi@123";

    @Autowired AuthService authService;
    @Autowired AdminUserService adminUsers;
    @Autowired DatabaseCleaner cleaner;

    private UserResponse user;

    @BeforeEach
    void setUp() {
        cleaner.clean();
        user = authService.register(new RegisterRequest(EMAIL, PASSWORD, "Người dùng", "0901234567"));
    }

    @Test
    @DisplayName("Sai 5 lần -> bị khoá, kể cả khi lần sau gõ đúng mật khẩu")
    void fiveFailures_lockEvenCorrectPassword() {
        for (int i = 1; i <= 4; i++) {
            assertThat(codeOf(EMAIL, WRONG)).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        }
        assertThat(codeOf(EMAIL, WRONG))
                .as("Lần sai thứ 5 báo khoá ngay để người dùng biết vì sao")
                .isEqualTo(ErrorCode.LOGIN_TEMPORARILY_LOCKED);

        BusinessException locked = errorOf(EMAIL, PASSWORD);
        assertThat(locked.code())
                .as("Đang bị khoá thì mật khẩu đúng cũng không vào được - nếu không, khoá vô nghĩa")
                .isEqualTo(ErrorCode.LOGIN_TEMPORARILY_LOCKED);
        assertThat(((Map<?, ?>) locked.details()).get("retryAfterSeconds"))
                .isInstanceOf(Long.class)
                .satisfies(v -> assertThat((Long) v).isPositive());
    }

    @Test
    @DisplayName("Email không tồn tại bị khoá y hệt - không dùng khoá để dò email được")
    void unknownEmail_isLockedTheSameWay() {
        String ghost = "khong-ton-tai@test.dev";
        for (int i = 1; i <= 4; i++) {
            assertThat(codeOf(ghost, WRONG)).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        }
        assertThat(codeOf(ghost, WRONG)).isEqualTo(ErrorCode.LOGIN_TEMPORARILY_LOCKED);
    }

    @Test
    @DisplayName("Đăng nhập đúng xoá bộ đếm: 4 lần sai hôm qua không cộng dồn với hôm nay")
    void successfulLogin_resetsCounter() {
        for (int i = 1; i <= 4; i++) codeOf(EMAIL, WRONG);
        authService.login(new LoginRequest(EMAIL, PASSWORD));

        for (int i = 1; i <= 4; i++) {
            assertThat(codeOf(EMAIL, WRONG)).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        }
    }

    @Test
    @DisplayName("Email khác hoa thường vẫn là cùng một bộ đếm")
    void counterIsCaseInsensitive() {
        for (int i = 1; i <= 4; i++) codeOf(EMAIL.toUpperCase(), WRONG);
        assertThat(codeOf(EMAIL, WRONG)).isEqualTo(ErrorCode.LOGIN_TEMPORARILY_LOCKED);
    }

    @Test
    @DisplayName("Admin gỡ khoá -> đăng nhập lại được ngay")
    void adminCanClearLockout() {
        for (int i = 1; i <= 5; i++) codeOf(EMAIL, WRONG);
        assertThat(adminUsers.detail(user.id()).loginLockedSeconds()).isPositive();

        adminUsers.clearLoginLockout(user.id());

        assertThat(authService.login(new LoginRequest(EMAIL, PASSWORD)).accessToken()).isNotBlank();
    }

    private ErrorCode codeOf(String email, String password) {
        return errorOf(email, password).code();
    }

    private BusinessException errorOf(String email, String password) {
        try {
            authService.login(new LoginRequest(email, password));
            throw new AssertionError("Mong đợi BusinessException nhưng đăng nhập thành công");
        } catch (BusinessException e) {
            return e;
        }
    }
}
