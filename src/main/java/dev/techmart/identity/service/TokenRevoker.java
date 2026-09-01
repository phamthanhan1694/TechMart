package dev.techmart.identity.service;

import dev.techmart.identity.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Thu hoi toan bo refresh token cua mot user, trong MOT TRANSACTION RIENG.
 *
 * <p><b>Vi sao phai tach ra - loi that da gap:</b> khi phat hien tai su dung refresh token,
 * {@code AuthService.refresh()} goi thu hoi ca chuoi roi <b>nem</b>
 * {@code REFRESH_TOKEN_REUSED}. Ngoai le do lam transaction rollback, va cuon theo ca
 * lenh thu hoi vua chay - nen tinh nang bao mat quan trong nhat cua module coi nhu
 * khong ton tai. Token bi danh cap van dung duoc binh thuong.
 *
 * <p>{@code REQUIRES_NEW} mo transaction moi va commit doc lap, nen viec thu hoi song sot
 * qua rollback cua transaction goi. Va phai la <b>bean rieng</b>: Spring AOP hoat dong
 * qua proxy nen goi noi bo trong cung class se bo qua annotation hoan toan.
 *
 * <p>Test bat duoc: {@code refresh_reusedToken_revokesEntireChain}.
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
