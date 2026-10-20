package dev.techmart.identity.service;

import dev.techmart.audit.domain.AuditAction;
import dev.techmart.audit.service.AuditLogger;
import dev.techmart.common.error.BusinessException;
import dev.techmart.common.error.ErrorCode;
import dev.techmart.common.web.PageResponse;
import dev.techmart.identity.domain.Role;
import dev.techmart.identity.domain.User;
import dev.techmart.identity.domain.UserStatus;
import dev.techmart.identity.dto.AccountDtos.*;
import dev.techmart.identity.repository.RefreshTokenRepository;
import dev.techmart.identity.repository.UserRepository;
import dev.techmart.identity.security.AuthPrincipal;
import dev.techmart.order.domain.RevenueStatuses;
import dev.techmart.order.dto.OrderDtos.OrderSummaryResponse;
import dev.techmart.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;

/**
 * Quản lý người dùng cho ADMIN: tra cứu kèm số liệu mua hàng, khoá/mở, đổi vai trò, tạo tài
 * khoản nhân viên, gỡ khoá đăng nhập tạm thời. Mọi thao tác ghi đều vào audit log.
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    /**
     * Whitelist cột sắp xếp. Tên từ client KHÔNG BAO GIỜ được ghép thẳng vào ORDER BY -
     * cùng lý do với tìm kiếm sản phẩm (README - Quyết định đáng nói).
     */
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "createdAt", "u.created_at",
            "totalSpent", "total_spent",
            "paidOrders", "paid_orders",
            "lastOrderAt", "last_order_at",
            "email", "u.email");

    private final NamedParameterJdbcTemplate jdbc;
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final OrderRepository orders;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptGuard loginAttempts;
    private final AuditLogger audit;
    private final Clock clock;

    // =================================================================== đọc

    /**
     * Danh sách người dùng kèm tổng chi tiêu - một câu SQL, không nạp đơn về Java.
     *
     * <p>Đánh đổi: sắp theo {@code totalSpent} buộc DB tính tổng cho MỌI người dùng khớp bộ
     * lọc trước khi cắt trang. Với vài nghìn khách là vài chục ms; tới hàng triệu khách thì
     * cần cột tổng hợp sẵn ({@code users.lifetime_spent}) cập nhật khi đơn đổi trạng thái.
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminUserRow> search(String keyword, Role role, UserStatus status, Pageable pageable) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("kw", likePattern(keyword))
                .addValue("role", role != null ? role.name() : null)
                .addValue("status", status != null ? status.name() : null)
                .addValue("limit", pageable.getPageSize())
                .addValue("offset", pageable.getOffset());

        String where = """
                 WHERE (CAST(:kw AS text) IS NULL
                        OR u.email ILIKE CAST(:kw AS text)
                        OR u.full_name ILIKE CAST(:kw AS text)
                        OR u.phone LIKE CAST(:kw AS text))
                   AND (CAST(:role AS text) IS NULL OR u.role = CAST(:role AS text))
                   AND (CAST(:status AS text) IS NULL OR u.status = CAST(:status AS text))
                """;

        long total = Objects.requireNonNull(jdbc.queryForObject(
                "SELECT COUNT(*) FROM users u " + where, params, Long.class));

        List<AdminUserRow> rows = jdbc.query("""
                SELECT u.id, u.email, u.full_name, u.phone, u.role, u.status, u.created_at,
                       COALESCE(s.paid_orders, 0) AS paid_orders,
                       COALESCE(s.total_spent, 0) AS total_spent,
                       s.last_order_at
                  FROM users u
                  LEFT JOIN LATERAL (
                        SELECT COUNT(*) FILTER (WHERE o.status IN %1$s)              AS paid_orders,
                               SUM(o.total_amount) FILTER (WHERE o.status IN %1$s) AS total_spent,
                               MAX(o.placed_at)                                     AS last_order_at
                          FROM orders o
                         WHERE o.user_id = u.id
                       ) s ON TRUE
                """.formatted(RevenueStatuses.SQL_IN)
                + where
                + " ORDER BY " + orderBy(pageable.getSort()) + ", u.id DESC LIMIT :limit OFFSET :offset",
                params, ROW_MAPPER);

        int totalPages = pageable.getPageSize() == 0 ? 0
                : (int) ((total + pageable.getPageSize() - 1) / pageable.getPageSize());
        return new PageResponse<>(rows, pageable.getPageNumber(), pageable.getPageSize(), total,
                totalPages, pageable.getOffset() + rows.size() < total);
    }

    @Transactional(readOnly = true)
    public AdminUserDetail detail(Long id) {
        AdminUserRow row = findRow(id);
        long lockedSeconds = loginAttempts.lockRemaining(row.email()).toSeconds();
        List<OrderSummaryResponse> recent = orders
                .findByUserIdOrderByPlacedAtDesc(id, PageRequest.of(0, 10))
                .map(OrderSummaryResponse::from)
                .getContent();
        return new AdminUserDetail(row, lockedSeconds, recent);
    }

    // =================================================================== ghi

    @Transactional
    public AdminUserRow create(CreateUserRequest req) {
        if (users.existsByEmailIgnoreCase(req.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        String phone = req.phone() == null || req.phone().isBlank() ? null : req.phone().trim();
        User user = users.saveAndFlush(new User(req.email().trim().toLowerCase(),
                passwordEncoder.encode(req.password()), req.fullName().trim(), phone, req.role()));

        audit.record(AuditAction.USER_CREATED, "USER", user.getId(),
                Map.of("email", user.getEmail(), "role", user.getRole().name()));
        return findRow(user.getId());
    }

    /**
     * Khoá / mở khoá tài khoản.
     *
     * <p>Khoá thì thu hồi mọi refresh token ngay trong transaction. Đánh đổi của JWT
     * stateless: access token đã phát vẫn dùng được tới khi hết hạn (tối đa 15 phút), vì
     * filter không tra DB mỗi request. Muốn chặn tức thì phải thêm danh sách chặn trong
     * Redis và tra ở mỗi request - một lần gọi mạng cho MỌI request để phục vụ một thao tác
     * hiếm. Ghi ở docs/13-trade-off.md.
     */
    @Transactional
    public AdminUserRow changeStatus(AuthPrincipal actor, Long id, ChangeStatusRequest req) {
        forbidSelf(actor, id);
        User user = require(id);
        UserStatus from = user.getStatus();
        if (from == req.status()) {
            return findRow(id);   // idempotent: khoá một tài khoản đã khoá không phải lỗi
        }

        if (req.status() == UserStatus.LOCKED) {
            if (user.getRole() == Role.ADMIN) {
                ensureAnotherActiveAdmin(id);
            }
            user.lock();
            refreshTokens.revokeAllForUser(id, clock.instant());
        } else {
            user.unlock();
        }
        users.flush();

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("email", user.getEmail());
        detail.put("from", from.name());
        detail.put("to", req.status().name());
        if (req.reason() != null && !req.reason().isBlank()) detail.put("reason", req.reason().trim());
        audit.record(req.status() == UserStatus.LOCKED ? AuditAction.USER_LOCKED : AuditAction.USER_UNLOCKED,
                "USER", id, detail);
        return findRow(id);
    }

    /**
     * Đổi vai trò. Thu hồi refresh token để lần làm mới kế tiếp phát token mang vai trò mới -
     * vai trò nằm trong JWT, không đọc lại từ DB mỗi request.
     */
    @Transactional
    public AdminUserRow changeRole(AuthPrincipal actor, Long id, ChangeRoleRequest req) {
        forbidSelf(actor, id);
        User user = require(id);
        Role from = user.getRole();
        if (from == req.role()) {
            return findRow(id);
        }
        if (from == Role.ADMIN && user.isActive()) {
            ensureAnotherActiveAdmin(id);
        }
        user.changeRole(req.role());
        refreshTokens.revokeAllForUser(id, clock.instant());
        users.flush();

        audit.record(AuditAction.USER_ROLE_CHANGED, "USER", id,
                Map.of("email", user.getEmail(), "from", from.name(), "to", req.role().name()));
        return findRow(id);
    }

    @Transactional
    public void clearLoginLockout(Long id) {
        User user = require(id);
        loginAttempts.clear(user.getEmail());
        audit.record(AuditAction.LOGIN_LOCKOUT_CLEARED, "USER", id, Map.of("email", user.getEmail()));
    }

    // =================================================================== nội bộ

    /** Tự khoá mình hay tự hạ quyền mình là cách nhanh nhất để không ai quản trị được hệ thống. */
    private static void forbidSelf(AuthPrincipal actor, Long targetId) {
        if (actor.id().equals(targetId)) {
            throw new BusinessException(ErrorCode.CANNOT_MODIFY_SELF);
        }
    }

    private void ensureAnotherActiveAdmin(Long leavingAdminId) {
        long others = users.lockActiveAdmins().stream()
                .filter(u -> !u.getId().equals(leavingAdminId))
                .count();
        if (others == 0) {
            throw new BusinessException(ErrorCode.LAST_ADMIN);
        }
    }

    private User require(Long id) {
        return users.findById(id)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.USER_NOT_FOUND, id));
    }

    private AdminUserRow findRow(Long id) {
        List<AdminUserRow> rows = jdbc.query("""
                SELECT u.id, u.email, u.full_name, u.phone, u.role, u.status, u.created_at,
                       COUNT(o.id) FILTER (WHERE o.status IN %1$s)                        AS paid_orders,
                       COALESCE(SUM(o.total_amount) FILTER (WHERE o.status IN %1$s), 0) AS total_spent,
                       MAX(o.placed_at)                                                   AS last_order_at
                  FROM users u
                  LEFT JOIN orders o ON o.user_id = u.id
                 WHERE u.id = :id
                 GROUP BY u.id
                """.formatted(RevenueStatuses.SQL_IN), Map.of("id", id), ROW_MAPPER);
        if (rows.isEmpty()) {
            throw BusinessException.notFound(ErrorCode.USER_NOT_FOUND, id);
        }
        return rows.get(0);
    }

    private static String orderBy(Sort sort) {
        List<String> parts = new ArrayList<>();
        for (Sort.Order order : sort) {
            String column = SORT_COLUMNS.get(order.getProperty());
            if (column != null) {
                parts.add(column + (order.isAscending() ? " ASC" : " DESC") + " NULLS LAST");
            }
        }
        return parts.isEmpty() ? "u.created_at DESC" : String.join(", ", parts);
    }

    /**
     * Thoát ký tự đại diện của LIKE. Không thoát thì khách gõ "%" hay "_" sẽ khớp mọi thứ -
     * không phải lỗ hổng, nhưng là kết quả sai và là cách khiến DB quét toàn bảng.
     */
    static String likePattern(String keyword) {
        if (keyword == null || keyword.isBlank()) return null;
        String escaped = keyword.trim()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private static final RowMapper<AdminUserRow> ROW_MAPPER = (rs, i) -> {
        Timestamp last = rs.getTimestamp("last_order_at");
        return new AdminUserRow(
                rs.getLong("id"),
                rs.getString("email"),
                rs.getString("full_name"),
                rs.getString("phone"),
                Role.valueOf(rs.getString("role")),
                UserStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant(),
                rs.getLong("paid_orders"),
                rs.getBigDecimal("total_spent"),
                last != null ? last.toInstant() : null);
    };
}
