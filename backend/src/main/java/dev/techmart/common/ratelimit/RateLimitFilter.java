package dev.techmart.common.ratelimit;

import tools.jackson.databind.ObjectMapper;
import dev.techmart.common.error.ApiError;
import dev.techmart.common.error.ErrorCode;
import dev.techmart.common.observability.BusinessMetrics;
import dev.techmart.identity.security.AuthPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Áp rate limit cho các endpoint nhạy cảm.
 *
 * <p>Hai tầng giới hạn ở endpoint flash sale: theo <b>user</b> và theo <b>IP</b>.
 * Cần cả hai vì một user có thể đổi IP, còn một IP (mạng công ty, NAT) có thể có nhiều
 * user thật phía sau.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    /** Endpoint, giới hạn theo user, giới hạn theo IP (mỗi phút). */
    private record Rule(String pathPrefix, String method, int perUser, int perIp) {

        boolean matches(HttpServletRequest request) {
            return request.getRequestURI().startsWith(pathPrefix)
                    && (method == null || method.equalsIgnoreCase(request.getMethod()));
        }
    }

    private static final List<Rule> RULES = List.of(
            // Chống dò mật khẩu.
            //
            // 60/phút/IP nghe có vẻ lỏng, nhưng giới hạn theo IP là công cụ cùn: một IP
            // sau NAT (văn phòng, phòng lab, mạng di động) có hàng trăm người dùng THẬT
            // phía sau. Đặt 5/phút/IP sẽ khoá nhầm cả nhóm họ chỉ vì một người gõ sai.
            //
            // Công cụ đúng cho brute force là giới hạn THEO TÀI KHOẢN (khoá sau N lần sai) -
            // đã có ở LoginAttemptGuard (ADR-0012). Giới hạn IP ở đây chỉ để chặn kiểu quét
            // thô thiển, nhiều tài khoản từ cùng một máy.
            new Rule("/api/v1/auth/login", "POST", 0, 60),
            new Rule("/api/v1/auth/register", "POST", 0, 20),
            // Đổi mật khẩu cũng là một cách đoán mật khẩu hiện tại - chặn theo người dùng
            new Rule("/api/v1/account/password", "POST", 5, 60),
            // Flash sale: hai tầng
            new Rule("/api/v1/flash-sales", "POST", 10, 100),
            // Trần chung cho luồng đặt hàng
            new Rule("/api/v1/orders", "POST", 30, 200)
    );

    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private final BusinessMetrics metrics;

    public RateLimitFilter(RateLimiter rateLimiter, ObjectMapper objectMapper, BusinessMetrics metrics) {
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {

        Rule rule = RULES.stream().filter(r -> r.matches(request)).findFirst().orElse(null);
        if (rule == null) {
            chain.doFilter(request, response);
            return;
        }

        RateLimiter.Decision decision = evaluate(rule, request);
        response.setHeader("X-RateLimit-Limit", String.valueOf(decision.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, decision.remaining())));

        if (!decision.allowed()) {
            metrics.recordRateLimited();
            response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
            writeTooManyRequests(request, response, decision);
            return;
        }
        chain.doFilter(request, response);
    }

    private RateLimiter.Decision evaluate(Rule rule, HttpServletRequest request) {
        // Kiểm theo IP trước: nó áp dụng cả cho request chưa đăng nhập
        RateLimiter.Decision byIp = rateLimiter.check(
                "rl:ip:" + rule.pathPrefix() + ":" + clientIp(request), rule.perIp(), rule.perIp());
        if (!byIp.allowed()) {
            return byIp;
        }
        Long userId = currentUserId();
        if (rule.perUser() > 0 && userId != null) {
            return rateLimiter.check(
                    "rl:user:" + rule.pathPrefix() + ":" + userId, rule.perUser(), rule.perUser());
        }
        return byIp;
    }

    private static Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthPrincipal principal) {
            return principal.id();
        }
        return null;
    }

    /**
     * Lấy IP thật khi đứng sau reverse proxy.
     * Chỉ tin X-Forwarded-For khi ứng dụng thật sự chạy sau proxy mình kiểm soát -
     * nếu không, client có thể tự đặt header này để vượt rate limit.
     */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void writeTooManyRequests(HttpServletRequest request, HttpServletResponse response,
                                      RateLimiter.Decision decision) throws IOException {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        response.setStatus(ErrorCode.RATE_LIMIT_EXCEEDED.status().value());
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getOutputStream(), ApiError.of(
                ErrorCode.RATE_LIMIT_EXCEEDED,
                "Quá nhiều yeu cau. Thử lại sau " + decision.retryAfterSeconds() + " giay.",
                request.getRequestURI(), traceId, null));
    }
}
