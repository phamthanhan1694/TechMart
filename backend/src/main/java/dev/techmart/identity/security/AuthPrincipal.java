package dev.techmart.identity.security;

import dev.techmart.identity.domain.Role;

/**
 * Danh tinh của người gọi, lấy từ token và đặt vào SecurityContext.
 *
 * <p>Mọi thao tác deu lấy userId từ đây, <b>không bao giờ</b> nhận userId từ client -
 * đây là cach chặn IDOR o goc (docs/07-security-checklist.md mục 2).
 */
public record AuthPrincipal(Long id, String email, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public boolean isStaffOrAdmin() {
        return role == Role.ADMIN || role == Role.STAFF;
    }
}
