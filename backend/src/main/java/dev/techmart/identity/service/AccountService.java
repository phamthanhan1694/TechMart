package dev.techmart.identity.service;

import dev.techmart.common.error.BusinessException;
import dev.techmart.common.error.ErrorCode;
import dev.techmart.identity.domain.User;
import dev.techmart.identity.dto.AccountDtos.ChangePasswordRequest;
import dev.techmart.identity.dto.AccountDtos.UpdateProfileRequest;
import dev.techmart.identity.dto.AuthDtos.UserResponse;
import dev.techmart.identity.repository.RefreshTokenRepository;
import dev.techmart.identity.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/** Người dùng tự quản lý hồ sơ và mật khẩu của mình. */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public AccountService(UserRepository users, RefreshTokenRepository refreshTokens,
                          PasswordEncoder passwordEncoder, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest req) {
        User user = require(userId);
        String phone = req.phone() == null || req.phone().isBlank() ? null : req.phone().trim();
        user.updateProfile(req.fullName().trim(), phone);
        return UserResponse.from(user);
    }

    /**
     * Đổi mật khẩu, rồi thu hồi MỌI refresh token của người dùng.
     *
     * <p>Người đổi mật khẩu thường vì nghi bị lộ - nếu phiên của kẻ gian vẫn sống thì đổi
     * mật khẩu vô nghĩa. Đánh đổi: chính người dùng cũng bị đăng xuất khỏi mọi thiết bị, và
     * access token đã phát vẫn dùng được tới khi hết hạn (tối đa 15 phút - xem ADR-0006).
     *
     * <p>Thu hồi chạy trong CÙNG transaction (không qua {@code TokenRevoker} REQUIRES_NEW):
     * ở đây ta muốn "đổi mật khẩu" và "thu hồi token" cùng thành công hoặc cùng thất bại.
     */
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest req) {
        User user = require(userId);
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.CURRENT_PASSWORD_INCORRECT);
        }
        if (req.currentPassword().equals(req.newPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_UNCHANGED);
        }
        user.changePassword(passwordEncoder.encode(req.newPassword()));
        int revoked = refreshTokens.revokeAllForUser(userId, clock.instant());
        log.info("User {} doi mat khau, thu hoi {} refresh token", userId, revoked);
    }

    private User require(Long userId) {
        return users.findById(userId)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.USER_NOT_FOUND, userId));
    }
}
