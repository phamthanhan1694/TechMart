package dev.techmart.identity.security;

import dev.techmart.identity.domain.Role;

/**
 * Danh tinh cua nguoi goi, lay tu token va dat vao SecurityContext.
 *
 * <p>Moi thao tac deu lay userId tu day, <b>khong bao gio</b> nhan userId tu client -
 * day la cach chan IDOR o goc (docs/07-security-checklist.md muc 2).
 */
public record AuthPrincipal(Long id, String email, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public boolean isStaffOrAdmin() {
        return role == Role.ADMIN || role == Role.STAFF;
    }
}
