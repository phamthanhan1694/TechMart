package dev.techmart.identity.service;

import dev.techmart.identity.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Thu hồi toàn bộ refresh token của một user, trong MỘT TRANSACTION RIÊNG.
 *
 * <p><b>Vì sao phải tách ra - lỗi thật đã gặp:</b> khi phát hiện tái sử dụng refresh token,
 * {@code AuthService.refresh()} gọi thu hồi cả chuỗi rồi <b>ném</b>
 * {@code REFRESH_TOKEN_REUSED}. Ngoại lệ đó làm transaction rollback, và cuốn theo cả
 * lệnh thu hồi vừa chạy - nên tính năng bảo mật quan trọng nhất của module coi như
 * không tồn tại. Token bị đánh cắp vẫn dùng được bình thường.
 *
 * <p>{@code REQUIRES_NEW} mở transaction mới và commit độc lập, nên việc thu hồi sống sót
 * qua rollback của transaction gọi. Và phải là <b>bean riêng</b>: Spring AOP hoạt động
 * qua proxy nên gọi nội bộ trong cùng class sẽ bỏ qua annotation hoàn toàn.
 *
 * <p>Test bắt được: {@code refresh_reusedToken_revokesEntireChain}.
 */
@Component
@RequiredArgsConstructor
public class TokenRevoker {

    private static final Logger log = LoggerFactory.getLogger(TokenRevoker.class);

    private final RefreshTokenRepository refreshTokens;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int revokeAllForUser(Long userId, String reason) {
        int revoked = refreshTokens.revokeAllForUser(userId, clock.instant());
        log.warn("Đã thu hồi {} refresh token của user {}. Lý do: {}", revoked, userId, reason);
        return revoked;
    }
}
