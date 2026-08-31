package dev.techmart.identity.domain;

public enum Role {
    CUSTOMER,
    STAFF,
    ADMIN;

    /** Spring Security quy uoc prefix ROLE_ cho hasRole(). */
    public String authority() {
        return "ROLE_" + name();
    }
}
