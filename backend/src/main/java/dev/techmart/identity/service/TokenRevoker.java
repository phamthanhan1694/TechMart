package dev.techmart.identity.service;

import dev.techmart.identity.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Thu hồi toàn bộ refresh token của một user, trong MỘT TRANSACTION RIENG.
 *
 * <p><b>Vì sao phải tach ra - lỗi thật đã gặp:</b> khi phát hiện tái sử dụng refresh token,
 * {@code AuthService.refresh()} gọi thu hồi ca chuỗi rồi <b>ném</b>
 * {@code REFRESH_TOKEN_REUSED}. Ngoai le đó làm transaction rollback, và cuon theo ca
 * lệnh thu hồi vua chay - nên tinh nang báo mat quan trọng nhất của module coi như
 * không tồn tại. Token bị đánh cắp vẫn đúng được binh thuong.
 *
 * <p>{@code REQUIRES_NEW} mo transaction mọi và commit doc lap, nên viec thu hồi song sot
 * qua rollback của transaction gọi. Va phải là <b>bean riêng</b>: Spring AOP hoạt động
 * qua proxy nên gọi nội bộ trong cũng class sẽ bỏ qua annotation hoan toan.
 *
 * <p>Test bat được: {@code refresh_reusedToken_revokesEntireChain}.
 */
@Component
public class TokenRevoker {

    private static final Logger log = LoggerFactory.getLogger(TokenRevoker.class);

    private final RefreshTokenRepository refreshTokens;
    private final Clock clock;

    public TokenRevoker(RefreshTokenRepository refreshTokens, Clock clock) {
        this.refreshTokens = refreshTokens;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int revokeAllForUser(Long userId, String reason) {
        int revoked = refreshTokens.revokeAllForUser(userId, clock.instant());
        log.warn("Da thu hoi {} refresh token cua user {}. Ly do: {}", revoked, userId, reason);
        return revoked;
    }
}
