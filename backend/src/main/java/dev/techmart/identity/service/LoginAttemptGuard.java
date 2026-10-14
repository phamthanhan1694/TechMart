package dev.techmart.identity.service;

import dev.techmart.common.config.AppProperties;
import dev.techmart.common.error.BusinessException;
import dev.techmart.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Khoá đăng nhập tạm thời theo TÀI KHOẢN sau N lần sai liên tiếp (ADR-0012).
 *
 * <p>Bổ sung cho rate limit theo IP ở {@code RateLimitFilter}, không thay thế nó. Giới hạn
 * theo IP không chặn được kẻ dò mật khẩu xoay vòng qua nhiều IP; giới hạn theo tài khoản thì
 * chặn được, vì mục tiêu của hắn là một tài khoản cố định.
 *
 * <p><b>Đánh đổi lớn nhất:</b> kẻ xấu biết email của nạn nhân có thể cố tình gõ sai để khoá
 * họ (từ chối dịch vụ nhắm vào một người). Giảm nhẹ bằng cách chỉ khoá <i>tạm thời</i>
 * (15 phút) chứ không khoá vĩnh viễn, và admin gỡ khoá được ngay. Giải pháp triệt để hơn là
 * CAPTCHA sau vài lần sai - cần dịch vụ bên ngoài, nằm ngoài phạm vì.
 *
 * <p><b>Email không tồn tại cũng bị đếm và bị khoá y hệt.</b> Nếu chỉ khoá email có thật thì
 * thông báo "tài khoản bị khoá" trở thành cách dò xem email nào đã đăng ký.
 *
 * <p><b>Fail-open khi Redis chết</b>, giống rate limit: khoá nhầm mọi người dùng thật tệ hơn
 * việc tạm mất một lớp chống dò mật khẩu (vẫn còn BCrypt strength 12 và giới hạn theo IP).
 *
 * <p>Khoá Redis dùng hash của email, không dùng email thô: người đọc được Redis không nên
 * đọc được danh sách email khách hàng.
 */
@Component
public class LoginAttemptGuard {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptGuard.class);

    private final StringRedisTemplate redis;
    private final RedisScript<Long> script;
    private final AppProperties.LoginLockout config;

    public LoginAttemptGuard(StringRedisTemplate redis, RedisScript<Long> loginAttemptScript,
                             AppProperties props) {
        this.redis = redis;
        this.script = loginAttemptScript;
        this.config = props.loginLockout();
    }

    /** Gọi TRƯỚC khi kiểm mật khẩu: tài khoản đang bị khoá thì không tốn BCrypt, không cho đoán tiếp. */
    public void ensureNotLocked(String email) {
        Duration remaining = lockRemaining(email);
        if (!remaining.isZero()) {
            throw locked(remaining);
        }
    }

    /** Ghi một lần sai. Lần sai làm chạm ngưỡng thì báo khoá luôn, để người dùng biết vì sao. */
    public void recordFailure(String email) {
        try {
            Long result = redis.execute(script, List.of(failKey(email), lockKey(email)),
                    String.valueOf(config.window().toMillis()),
                    String.valueOf(config.maxAttempts()),
                    String.valueOf(config.lockDuration().toMillis()));
            if (result != null && result == -1L) {
                log.warn("Khoa dang nhap tam thoi {} sau {} lan sai", maskEmail(email), config.maxAttempts());
                throw locked(config.lockDuration());
            }
        } catch (DataAccessException e) {
            log.warn("Redis khong kha dung, bo qua dem lan dang nhap sai (fail-open): {}", e.getMessage());
        }
    }

    /** Đăng nhập đúng thì xoá bộ đếm: 4 lần sai hôm qua không được cộng dồn với lần sai hôm nay. */
    public void recordSuccess(String email) {
        try {
            redis.delete(failKey(email));
        } catch (DataAccessException e) {
            log.warn("Redis khong kha dung, khong xoa duoc bo dem dang nhap: {}", e.getMessage());
        }
    }

    /** Admin gỡ khoá cho khách gọi lên tổng đài. */
    public void clear(String email) {
        try {
            redis.delete(List.of(failKey(email), lockKey(email)));
        } catch (DataAccessException e) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    /** Thời gian khoá còn lại; {@link Duration#ZERO} nếu không bị khoá hoặc Redis không trả lời. */
    public Duration lockRemaining(String email) {
        try {
            Long ttl = redis.getExpire(lockKey(email), java.util.concurrent.TimeUnit.MILLISECONDS);
            return ttl != null && ttl > 0 ? Duration.ofMillis(ttl) : Duration.ZERO;
        } catch (DataAccessException e) {
            log.warn("Redis khong kha dung, bo qua kiem tra khoa dang nhap (fail-open): {}", e.getMessage());
            return Duration.ZERO;
        }
    }

    private static BusinessException locked(Duration remaining) {
        long seconds = Math.max(1, (remaining.toMillis() + 999) / 1000);
        return new BusinessException(ErrorCode.LOGIN_TEMPORARILY_LOCKED,
                "Dang nhap sai qua nhieu lan, thu lai sau " + Math.max(1, (seconds + 59) / 60) + " phut",
                Map.of("retryAfterSeconds", seconds));
    }

    private static String failKey(String email) {
        return "login:fail:" + digest(email);
    }

    private static String lockKey(String email) {
        return "login:lock:" + digest(email);
    }

    private static String digest(String email) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(email.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM thieu SHA-256", e);
        }
    }

    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        return at <= 1 ? "***" : email.charAt(0) + "***" + email.substring(at);
    }
}
