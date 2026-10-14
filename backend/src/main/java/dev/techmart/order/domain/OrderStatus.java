package dev.techmart.order.domain;

public enum OrderStatus {
    PENDING_PAYMENT,
    PAID,
    PACKED,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    REFUNDED;

    public boolean isTerminal() {
        return this == DELIVERED || this == CANCELLED || this == REFUNDED;
    }
}
