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

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens,
                       PasswordEncoder passwordEncoder, JwtService jwtService, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.clock = clock;
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
                Role.CUSTOMER);          // dang ky cong khai luon la CUSTOMER, khong nhan role tu client
        return UserResponse.from(users.save(user));
    }

    @Transactional
    public TokenResponse login(LoginRequest req) {
        User user = users.findByEmailIgnoreCase(req.email())
                // Sai email va sai mat khau tra CUNG mot ma loi: khong tiet lo email nao ton tai
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }
        return issueTokens(user);
    }

    /**
     * Xoay vong refresh token, kem phat hien tai su dung.
     *
     * <p>Mot token da bi thu hoi ma van duoc gui len nghia la co hai ben dang giu no -
     * gan nhu chac chan token da bi danh cap. Khi do thu hoi <b>ca chuoi</b> cua user,
     * buoc dang nhap lai (docs/07-security-checklist.md muc 1).
     */
    @Transactional
    public TokenResponse refresh(RefreshRequest req) {
        Instant now = clock.instant();
        String hash = jwtService.hashRefreshToken(req.refreshToken());

        RefreshToken stored = refreshTokens.findByTokenHash(hash)
                .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID));

        if (stored.isRevoked()) {
            Long userId = stored.getUser().getId();
            int revoked = refreshTokens.revokeAllForUser(userId, now);
            log.warn("Phat hien tai su dung refresh token cua user {}. Da thu hoi {} token con song.",
                    userId, revoked);
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

    /** Logout thu hoi that o phia server, khong chi xoa token o client. */
    @Transactional
    public void logout(RefreshRequest req) {
        String hash = jwtService.hashRefreshToken(req.refreshToken());
        refreshTokens.findByTokenHash(hash).ifPresent(t -> t.revoke(clock.instant()));
    }

    @Transactional
    public void logoutAllDevices(Long userId) {
        refreshTokens.revokeAllForUser(userId, clock.instant());
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

    /** Gia tri thô chi ton tai trong bo nho, DB chi giu hash. */
    private record TokenResult(String rawValue, RefreshToken entity) {}
}
