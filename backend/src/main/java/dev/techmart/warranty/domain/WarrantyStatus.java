package dev.techmart.warranty.domain;

public enum WarrantyStatus {
    REQUESTED,
    APPROVED,
    REJECTED,
    COMPLETED;

    public boolean isClosed() {
        return this == REJECTED || this == COMPLETED;
    }
}
