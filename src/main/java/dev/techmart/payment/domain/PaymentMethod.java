package dev.techmart.payment.domain;

public enum PaymentMethod {
    /** Cong thanh toan gia dung trong project - du de the hien luong idempotency + webhook. */
    MOCK_CARD,
    /** Thanh toan khi nhan hang. */
    COD
}
