package dev.techmart.identity.security;

import dev.techmart.common.config.AppProperties;
import dev.techmart.common.error.BusinessException;
import dev.techmart.common.error.ErrorCode;
import dev.techmart.identity.domain.Role;
import dev.techmart.identity.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;

/**
 * Sinh và xác thực token.
 *
 * <p><b>Access token</b> là JWT ký HS256, sống 15 phút. Ngắn vì JWT không thu hồi được -
 * ai cầm được là dùng được cho tới khi hết hạn, nên rút ngắn tuổi thọ là cách duy nhất
 * giới hạn thiệt hại.
 *
 * <p><b>Refresh token</b> KHÔNG phải JWT mà là chuỗi ngẫu nhiên 256 bit, lưu hash SHA-256
 * trong DB. Lý do: refresh token phải thu hồi được, mà muốn thu hồi thì phải tra DB -
 * lúc đó dùng JWT không còn lợi ích gì mà còn thêm bề mặt tấn công.
 */
@Service
public class JwtService {

    private static final String CLAIM_ROLE = "role";
    private static final int REFRESH_TOKEN_BYTES = 32;

    private final SecretKey key;
    private final AppProperties props;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public JwtService(AppProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
        byte[] secret = props.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret phải dài tối thiểu 32 byte cho HS256. Sinh mới: openssl rand -base64 48");
        }
        this.key = Keys.hmacShaKeyFor(secret);
    }

    public String generateAccessToken(User user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_ROLE, user.getRole().name())
                .claim("email", user.getEmail())
                .issuer(props.jwt().issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(props.jwt().accessTtl())))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Đọc token và dựng danh tính người gọi.
     * Chỉ chấp nhận HS256 và đúng issuer - từ chối token tự khai thuật toán khác.
     */
    public AuthPrincipal parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(props.jwt().issuer())
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return new AuthPrincipal(
                    Long.valueOf(claims.getSubject()),
                    claims.get("email", String.class),
                    Role.valueOf(claims.get(CLAIM_ROLE, String.class)));

        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
    }

    /** Chuỗi ngẫu nhiên 256 bit, chỉ trả về cho client một lần duy nhất. */
    public String generateRefreshTokenValue() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 (không phải BCrypt): token đã là ngẫu nhiên 256 bit nên không cần làm chậm. */
    public String hashRefreshToken(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 phải có sẵn trên mọi JVM", e);
        }
    }

    public Instant refreshTokenExpiry() {
        return clock.instant().plus(props.jwt().refreshTtl());
    }

    public long accessTokenTtlSeconds() {
        return props.jwt().accessTtl().toSeconds();
    }
}
