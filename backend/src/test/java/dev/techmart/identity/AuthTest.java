package dev.techmart.identity;

import dev.techmart.common.error.BusinessException;
import dev.techmart.common.error.ErrorCode;
import dev.techmart.identity.dto.AuthDtos.*;
import dev.techmart.identity.repository.RefreshTokenRepository;
import dev.techmart.identity.service.AuthService;
import dev.techmart.support.DatabaseCleaner;
import dev.techmart.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Xác thực và vòng đời refresh token - xem ADR-0006.
 *
 * <p>Nhóm test đáng giá nhất ở đây là phát hiện tái sử dụng refresh token: nó là thứ
 * biến "có refresh token" thành "có thể phát hiện token bị đánh cắp".
 */
@DisplayName("Xác thực")
class AuthTest extends IntegrationTestBase {

    private static final String EMAIL = "nguoidung@test.dev";
    private static final String PASSWORD = "MatKhau@123";

    @Autowired AuthService authService;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired DatabaseCleaner cleaner;

    @BeforeEach
    void setUp() {
        cleaner.clean();
        authService.register(new RegisterRequest(EMAIL, PASSWORD, "Người dùng", "0901234567"));
    }

    @Test
    @DisplayName("Đăng ký trùng email -> 409")
    void register_duplicateEmail_isRejected() {
        assertThatThrownBy(() -> authService.register(
                new RegisterRequest(EMAIL, PASSWORD, "Người khác", "0909999999")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("Sai mật khẩu và email không tồn tại trả CÙNG một mã lỗi")
    void login_wrongPassword_returns401_withSameCodeAsUnknownEmail() {
        ErrorCode wrongPassword = codeOf(() ->
                authService.login(new LoginRequest(EMAIL, "SaiRoi@123")));
        ErrorCode unknownEmail = codeOf(() ->
                authService.login(new LoginRequest("khong-ton-tai@test.dev", PASSWORD)));

        assertThat(wrongPassword).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        assertThat(unknownEmail)
                .as("Phân biệt hai trường hợp này là để lộ email nào tồn tại trong hệ thống")
                .isEqualTo(wrongPassword);
    }

    @Test
    @DisplayName("Refresh xoay vòng: token cũ mất hiệu lực ngay")
    void refresh_rotatesToken_oldTokenBecomesInvalid() {
        TokenResponse first = authService.login(new LoginRequest(EMAIL, PASSWORD));
        TokenResponse second = authService.refresh(new RefreshRequest(first.refreshToken()));

        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(second.accessToken()).isNotBlank();

        // Token cũ đã bị thu hồi - dùng lại phải bị từ chối
        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(first.refreshToken())))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Dùng lại refresh token đã thu hồi -> thu hồi CẢ chuỗi token của user")
    void refresh_reusedToken_revokesEntireChain() {
        TokenResponse t1 = authService.login(new LoginRequest(EMAIL, PASSWORD));
        TokenResponse t2 = authService.refresh(new RefreshRequest(t1.refreshToken()));
        TokenResponse t3 = authService.refresh(new RefreshRequest(t2.refreshToken()));

        // Kẻ tấn công dùng lại t1 (đã bị thu hồi từ lâu)
        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(t1.refreshToken())))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.REFRESH_TOKEN_REUSED);

        // Cả chuỗi bị thu hồi: t3 - token còn đang hợp lệ của người dùng thật - cũng chết.
        // Đây là cố ý: không biết ai là nạn nhân nên buộc cả hai đăng nhập lại.
        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(t3.refreshToken())))
                .as("Token đang hợp lệ cũng phải bị thu hồi khi phát hiện token bị đánh cắp")
                .isInstanceOf(BusinessException.class);

        assertThat(refreshTokens.findAll())
                .as("Không còn token nào còn song")
                .allMatch(t -> t.getRevokedAt() != null);
    }

    @Test
    @DisplayName("Refresh token lưu trong DB là HASH, không phải giá trị thô")
    void refreshToken_isStoredHashed() {
        TokenResponse tokens = authService.login(new LoginRequest(EMAIL, PASSWORD));

        assertThat(refreshTokens.findAll())
                .isNotEmpty()
                .allSatisfy(t -> {
                    assertThat(t.getTokenHash())
                            .as("DB ro ri thi token van khong dung duoc")
                            .isNotEqualTo(tokens.refreshToken());
                    assertThat(t.getTokenHash()).hasSize(64);   // SHA-256 dạng hex
                });
    }

    @Test
    @DisplayName("Logout thu hồi thật ở phía server")
    void logout_revokesTokenServerSide() {
        TokenResponse tokens = authService.login(new LoginRequest(EMAIL, PASSWORD));
        authService.logout(new RefreshRequest(tokens.refreshToken()));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(tokens.refreshToken())))
                .isInstanceOf(BusinessException.class);
    }

    private static ErrorCode codeOf(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Mong đợi một BusinessException nhưng không có");
        } catch (BusinessException e) {
            return e.code();
        }
    }
}
