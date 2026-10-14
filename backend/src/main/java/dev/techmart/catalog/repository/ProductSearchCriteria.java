package dev.techmart.catalog.repository;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Bo lọc của GET /api/v1/products.
 *
 * @param spec các cấp key/value lọc trên cot JSONB, vd {@code {"ram": "16GB"}}.
 *             Key đã được whitelist ở tầng web trước khi tới đây.
 */
public record ProductSearchCriteria(
        String keyword,
        Long categoryId,
        Long brandId,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Map<String, String> spec,
        Boolean inStock
) {
    public boolean hasSpecFilter() {
        return spec != null && !spec.isEmpty();
    }
}
