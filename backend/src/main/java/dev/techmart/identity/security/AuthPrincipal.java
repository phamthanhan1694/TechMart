package dev.techmart.identity.security;

import dev.techmart.identity.domain.Role;

/**
 * Danh tính của người gọi, lấy từ token và đặt vào SecurityContext.
 *
 * <p>Mọi thao tác đều lấy userId từ đây, <b>không bao giờ</b> nhận userId từ client -
 * đây là cách chặn IDOR ở gốc (docs/07-security-checklist.md mục 2).
 */
public record AuthPrincipal(Long id, String email, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public boolean isStaffOrAdmin() {
        return role == Role.ADMIN || role == Role.STAFF;
    }
}
