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
 * biến "có refresh token" thanh "có thể phát hiện token bị đánh cắp".
 */
@DisplayName("Xac thuc")
class AuthTest extends IntegrationTestBase {

    private static final String EMAIL = "nguoidung@test.dev";
    private static final String PASSWORD = "MatKhau@123";

    @Autowired AuthService authService;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired DatabaseCleaner cleaner;

    @BeforeEach
    void setUp() {
        cleaner.clean();
        authService.register(new RegisterRequest(EMAIL, PASSWORD, "Nguoi dung", "0901234567"));
    }

    @Test
    @DisplayName("Dang ky trung email -> 409")
    void register_duplicateEmail_isRejected() {
        assertThatThrownBy(() -> authService.register(
                new RegisterRequest(EMAIL, PASSWORD, "Nguoi khac", "0909999999")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("Sai mat khau va email khong ton tai tra CUNG mot ma loi")
    void login_wrongPassword_returns401_withSameCodeAsUnknownEmail() {
        ErrorCode wrongPassword = codeOf(() ->
                authService.login(new LoginRequest(EMAIL, "SaiRoi@123")));
        ErrorCode unknownEmail = codeOf(() ->
                authService.login(new LoginRequest("khong-ton-tai@test.dev", PASSWORD)));

        assertThat(wrongPassword).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        assertThat(unknownEmail)
                .as("Phan biet hai truong hop nay la de lo email nao ton tai trong he thong")
                .isEqualTo(wrongPassword);
    }

    @Test
    @DisplayName("Refresh xoay vong: token cu mat hieu luc ngay")
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
    @DisplayName("Dung lai refresh token da thu hoi -> thu hoi CA CHUOI token cua user")
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
                .as("Token dang hop le cung phai bi thu hoi khi phat hien token bi danh cap")
                .isInstanceOf(BusinessException.class);

        assertThat(refreshTokens.findAll())
                .as("Khong con token nao con song")
                .allMatch(t -> t.getRevokedAt() != null);
    }

    @Test
    @DisplayName("Refresh token luu trong DB la HASH, khong phai gia tri tho")
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
    @DisplayName("Logout thu hoi that o phia server")
    void logout_revokesTokenServerSide() {
        TokenResponse tokens = authService.login(new LoginRequest(EMAIL, PASSWORD));
        authService.logout(new RefreshRequest(tokens.refreshToken()));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(tokens.refreshToken())))
                .isInstanceOf(BusinessException.class);
    }

    private static ErrorCode codeOf(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Mong doi mot BusinessException nhung khong co");
        } catch (BusinessException e) {
            return e.code();
        }
    }
}
