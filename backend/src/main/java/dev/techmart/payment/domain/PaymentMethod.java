package dev.techmart.payment.domain;

public enum PaymentMethod {
    /** Cổng thanh toán giả dùng trong project - đủ để thể hiện luồng idempotency + webhook. */
    MOCK_CARD,
    /** Thanh toán khi nhận hàng. */
    COD
}
