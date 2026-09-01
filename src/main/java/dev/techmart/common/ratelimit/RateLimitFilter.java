package dev.techmart.common.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Ap rate limit cho cac endpoint nhay cam.
 *
 * <p>Hai tang gioi han o endpoint flash sale: theo <b>user</b> va theo <b>IP</b>.
 * Can ca hai vi mot user co the doi IP, con mot IP (mang cong ty, NAT) co the co nhieu
 * user that phia sau.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    /** Endpoint, gioi han theo user, gioi han theo IP (moi phut). */
    private record Rule(String pathPrefix, String method, int perUser, int perIp) {

        boolean matches(HttpServletRequest request) {
            return request.getRequestURI().startsWith(pathPrefix)
                    && (method == null || method.equalsIgnoreCase(request.getMethod()));
        }
    }

    private static final List<Rule> RULES = List.of(
            // Chong do mat khau
            new Rule("/api/v1/auth/login", "POST", 0, 5),
            new Rule("/api/v1/auth/register", "POST", 0, 10),
            // Flash sale: hai tang
            new Rule("/api/v1/flash-sales", "POST", 10, 100),
            // Tran chung cho luong dat hang
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
        // Kiem theo IP truoc: no ap dung ca cho request chua dang nhap
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
     * Lay IP that khi dung sau reverse proxy.
     * Chi tin X-Forwarded-For khi ung dung that su chay sau proxy minh kiem soat -
     * neu khong, client co the tu dat header nay de vuot rate limit.
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
                "Qua nhieu yeu cau. Thu lai sau " + decision.retryAfterSeconds() + " giay.",
                request.getRequestURI(), traceId, null));
    }
}
