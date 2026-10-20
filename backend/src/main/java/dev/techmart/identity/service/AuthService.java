package dev.techmart.identity.service;

import dev.techmart.common.error.BusinessException;
import dev.techmart.common.error.ErrorCode;
import dev.techmart.identity.domain.RefreshToken;
import dev.techmart.identity.domain.Role;
import dev.techmart.identity.domain.User;
import dev.techmart.identity.dto.AuthDtos.*;
import dev.techmart.identity.repository.RefreshTokenRepository;
import dev.techmart.identity.repository.UserRepository;
import dev.techmart.identity.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenRevoker tokenRevoker;
    private final LoginAttemptGuard loginAttempts;
    private final Clock clock;
    /** Hash của một mật khẩu ngẫu nhiên, chỉ để so khi email không tồn tại. */
    private final String dummyHash;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens,
                       PasswordEncoder passwordEncoder, JwtService jwtService,
                       TokenRevoker tokenRevoker, LoginAttemptGuard loginAttempts, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokenRevoker = tokenRevoker;
        this.loginAttempts = loginAttempts;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode(java.util.UUID.randomUUID().toString());
    }

    @Transactional
    public UserResponse register(RegisterRequest req) {
        if (users.existsByEmailIgnoreCase(req.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        User user = new User(
                req.email().toLowerCase(),
                passwordEncoder.encode(req.password()),
                req.fullName(),
                req.phone(),
                Role.CUSTOMER);          // đăng ký công khai luôn là CUSTOMER, không nhận role từ client
        return UserResponse.from(users.save(user));
    }

    /**
     * Đăng nhập, có khoá tạm thời theo tài khoản sau N lần sai (ADR-0012).
     *
     * <p>Thứ tự kiểm tra có chủ đích: kiểm khoá TRƯỚC khi chạy BCrypt, để tài khoản đang bị
     * khoá không cho đoán thêm lần nào; kiểm trạng thái LOCKED của admin SAU khi đúng mật
     * khẩu, để thông báo "tài khoản bị khoá" không trở thành cách dò email.
     */
    @Transactional
    public TokenResponse login(LoginRequest req) {
        String email = req.email().trim().toLowerCase();
        loginAttempts.ensureNotLocked(email);

        Optional<User> found = users.findByEmailIgnoreCase(email);
        // Email không tồn tại vẫn chạy BCrypt với hash giả: không có nhánh này thì trả lời
        // nhanh hơn ~250ms, và thời gian phản hồi tự tiết lộ email nào đã đăng ký.
        String hash = found.map(User::getPasswordHash).orElse(dummyHash);
        boolean matches = passwordEncoder.matches(req.password(), hash);

        if (found.isEmpty() || !matches) {
            loginAttempts.recordFailure(email);
            // Sai email và sai mật khẩu trả CÙNG một mã lỗi: không tiết lộ email nào tồn tại
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        loginAttempts.recordSuccess(email);

        User user = found.get();
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }
        return issueTokens(user);
    }

    /**
     * Xoay vòng refresh token, kèm phát hiện tái sử dụng.
     *
     * <p>Một token đã bị thu hồi mà vẫn được gửi lên nghĩa là có hai bên đang giữ nó -
     * gần như chắc chắn token đã bị đánh cắp. Khi đó thu hồi <b>cả chuỗi</b> của user,
     * buộc đăng nhập lại (docs/07-security-checklist.md mục 1).
     */
    @Transactional
    public TokenResponse refresh(RefreshRequest req) {
        Instant now = clock.instant();
        String hash = jwtService.hashRefreshToken(req.refreshToken());

        RefreshToken stored = refreshTokens.findByTokenHash(hash)
                .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID));

        if (stored.isRevoked()) {
            // Thu hồi phải chạy ở TRANSACTION RIÊNG: ngay sau đây ta ném ngoại lệ, và
            // ngoại lệ đó sẽ rollback transaction hiện tại - cuốn theo cả lệnh thu hồi.
            // Xem TokenRevoker.
            tokenRevoker.revokeAllForUser(stored.getUser().getId(),
                    "Phát hiện tái sử dụng refresh token đã bị thu hồi");
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_REUSED);
        }
        if (stored.isExpired(now)) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        }

        User user = stored.getUser();
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }

        TokenResult next = createRefreshToken(user);
        stored.replaceWith(next.entity(), now);

        return TokenResponse.of(
                jwtService.generateAccessToken(user), next.rawValue(), jwtService.accessTokenTtlSeconds());
    }

    /** Logout thu hồi thật ở phía server, không chỉ xoá token ở client. */
    @Transactional
    public void logout(RefreshRequest req) {
        String hash = jwtService.hashRefreshToken(req.refreshToken());
        refreshTokens.findByTokenHash(hash).ifPresent(t -> t.revoke(clock.instant()));
    }

    public void logoutAllDevices(Long userId) {
        tokenRevoker.revokeAllForUser(userId, "Người dùng đăng xuất khỏi mọi thiết bị");
    }

    @Transactional(readOnly = true)
    public UserResponse me(Long userId) {
        return users.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private TokenResponse issueTokens(User user) {
        TokenResult refresh = createRefreshToken(user);
        return TokenResponse.of(
                jwtService.generateAccessToken(user), refresh.rawValue(), jwtService.accessTokenTtlSeconds());
    }

    private TokenResult createRefreshToken(User user) {
        String raw = jwtService.generateRefreshTokenValue();
        RefreshToken entity = refreshTokens.saveAndFlush(
                new RefreshToken(user, jwtService.hashRefreshToken(raw), jwtService.refreshTokenExpiry()));
        return new TokenResult(raw, entity);
    }

    /** Giá trị thô chỉ tồn tại trong bộ nhớ, DB chỉ giữ hash. */
    private record TokenResult(String rawValue, RefreshToken entity) {}
}
