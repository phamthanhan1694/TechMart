package dev.techmart.promotion.domain;

public enum DiscountType {
    /** Giảm theo phần trăm, có thể có trần maxDiscount. */
    PERCENT,
    /** Giảm số tiền cố định. */
    FIXED
}
