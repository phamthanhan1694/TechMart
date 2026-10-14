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
@DisplayName("Khoa dang nhap theo tai khoan")
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
        user = authService.register(new RegisterRequest(EMAIL, PASSWORD, "Nguoi dung", "0901234567"));
    }

    @Test
    @DisplayName("Sai 5 lan -> bi khoa, ke ca khi lan sau go dung mat khau")
    void fiveFailures_lockEvenCorrectPassword() {
        for (int i = 1; i <= 4; i++) {
            assertThat(codeOf(EMAIL, WRONG)).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        }
        assertThat(codeOf(EMAIL, WRONG))
                .as("Lan sai thu 5 bao khoa ngay de nguoi dung biet vi sao")
                .isEqualTo(ErrorCode.LOGIN_TEMPORARILY_LOCKED);

        BusinessException locked = errorOf(EMAIL, PASSWORD);
        assertThat(locked.code())
                .as("Dang bi khoa thi mat khau dung cung khong vao duoc - neu khong, khoa vo nghia")
                .isEqualTo(ErrorCode.LOGIN_TEMPORARILY_LOCKED);
        assertThat(((Map<?, ?>) locked.details()).get("retryAfterSeconds"))
                .isInstanceOf(Long.class)
                .satisfies(v -> assertThat((Long) v).isPositive());
    }

    @Test
    @DisplayName("Email khong ton tai bi khoa y het - khong dung khoa de do email duoc")
    void unknownEmail_isLockedTheSameWay() {
        String ghost = "khong-ton-tai@test.dev";
        for (int i = 1; i <= 4; i++) {
            assertThat(codeOf(ghost, WRONG)).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        }
        assertThat(codeOf(ghost, WRONG)).isEqualTo(ErrorCode.LOGIN_TEMPORARILY_LOCKED);
    }

    @Test
    @DisplayName("Dang nhap dung xoa bo dem: 4 lan sai hom qua khong cong don voi hom nay")
    void successfulLogin_resetsCounter() {
        for (int i = 1; i <= 4; i++) codeOf(EMAIL, WRONG);
        authService.login(new LoginRequest(EMAIL, PASSWORD));

        for (int i = 1; i <= 4; i++) {
            assertThat(codeOf(EMAIL, WRONG)).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        }
    }

    @Test
    @DisplayName("Email khac hoa thuong van la cung mot bo dem")
    void counterIsCaseInsensitive() {
        for (int i = 1; i <= 4; i++) codeOf(EMAIL.toUpperCase(), WRONG);
        assertThat(codeOf(EMAIL, WRONG)).isEqualTo(ErrorCode.LOGIN_TEMPORARILY_LOCKED);
    }

    @Test
    @DisplayName("Admin go khoa -> dang nhap lai duoc ngay")
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
            throw new AssertionError("Mong doi BusinessException nhung dang nhap thanh cong");
        } catch (BusinessException e) {
            return e;
        }
    }
}
