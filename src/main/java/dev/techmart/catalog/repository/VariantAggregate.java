package dev.techmart.catalog.repository;

import java.math.BigDecimal;

/**
 * Gia thap nhat / cao nhat va tong hang con ban duoc cua mot san pham.
 *
 * <p>Ton tai de tranh N+1: trang danh sach san pham can khoang gia cua tung san pham;
 * neu de moi Product tu nap variants cua no thi 20 san pham = 21 query. Thay vao do
 * gom lai mot query GROUP BY duy nhat cho ca trang.
 */
public record VariantAggregate(
        Long productId,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Long availableQuantity
) {}
