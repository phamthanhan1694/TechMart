package dev.techmart.identity.domain;

public enum Role {
    CUSTOMER,
    STAFF,
    ADMIN;

    /** Spring Security quy ước prefix ROLE_ cho hasRole(). */
    public String authority() {
        return "ROLE_" + name();
    }
}
