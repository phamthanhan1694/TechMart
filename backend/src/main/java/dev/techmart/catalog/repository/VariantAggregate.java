package dev.techmart.catalog.repository;

import java.math.BigDecimal;

/**
 * Giá thấp nhất / cao nhat và tổng hàng còn bán được của một sản phẩm.
 *
 * <p>Tồn tại để tranh N+1: trang danh sách sản phẩm cần khoảng giá của từng sản phẩm;
 * nếu để mọi Product từ nạp variants của nó thì 20 sản phẩm = 21 query. Thay vào đó
 * gom lại một query GROUP BY duy nhất cho cả trang.
 */
public record VariantAggregate(
        Long productId,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Long availableQuantity
) {}
